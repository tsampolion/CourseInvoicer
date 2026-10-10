package gr.eduinvoice.ui.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.Money
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.model.sumOfMoney
import gr.eduinvoice.core.repository.GroupRepository
import gr.eduinvoice.core.repository.InvoiceRepository
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.StudentRepository
import gr.eduinvoice.core.usecase.CreateInvoice
import gr.eduinvoice.ui.common.launchCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CreateInvoiceUiState(
    val loading: Boolean = true,
    val students: List<Student> = emptyList(),
    val studentId: Long? = null,
    val lessons: List<Lesson> = emptyList(),
    val groupNames: Map<Long, String> = emptyMap(),
    val selected: Set<Long> = emptySet(),
    val issueDate: LocalDate,
    val notes: String = "",
    val markPaid: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val created: Invoice? = null,
) {
    val total: Money get() = lessons.filter { it.id in selected }.sumOfMoney { it.charge }
}

class CreateInvoiceViewModel(
    initialStudentId: Long,
    private val students: StudentRepository,
    private val groups: GroupRepository,
    private val lessons: LessonRepository,
    invoices: InvoiceRepository,
    today: LocalDate = LocalDate.now(),
) : ViewModel() {
    private val createInvoice = CreateInvoice(lessons, invoices)
    private val mutableState = MutableStateFlow(CreateInvoiceUiState(issueDate = today))
    val state: StateFlow<CreateInvoiceUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val pickable = students.observeStudents().first().filter { !it.archived || it.id == initialStudentId }
            val groupNames = groups.observeGroups().first().associate { it.id to it.name }
            mutableState.update { it.copy(loading = false, students = pickable, groupNames = groupNames) }
            if (pickable.any { it.id == initialStudentId }) selectStudent(initialStudentId)
        }
    }

    fun selectStudent(id: Long) {
        mutableState.update { it.copy(studentId = id, lessons = emptyList(), selected = emptySet()) }
        viewModelScope.launch {
            val open = lessons.observeLessonsOfStudent(id).first()
                .filter { it.isInvoiceable }
                .sortedWith(compareBy({ it.date }, { it.startTime }))
            mutableState.update { state ->
                if (state.studentId != id) state else state.copy(lessons = open, selected = open.map { it.id }.toSet())
            }
        }
    }

    fun toggle(lessonId: Long) = mutableState.update {
        it.copy(selected = if (lessonId in it.selected) it.selected - lessonId else it.selected + lessonId)
    }

    fun selectAll(all: Boolean) = mutableState.update {
        it.copy(selected = if (all) it.lessons.map { lesson -> lesson.id }.toSet() else emptySet())
    }

    fun setIssueDate(date: LocalDate) = mutableState.update { it.copy(issueDate = date) }

    fun setNotes(text: String) = mutableState.update { it.copy(notes = text) }

    fun setMarkPaid(value: Boolean) = mutableState.update { it.copy(markPaid = value) }

    fun create() {
        val current = state.value
        val studentId = current.studentId ?: return
        mutableState.update { it.copy(saving = true) }
        launchCatching({ error -> mutableState.update { it.copy(saving = false, message = error) } }) {
            val invoice = createInvoice(studentId, current.selected, current.issueDate, current.notes, current.markPaid)
            mutableState.update { it.copy(saving = false, created = invoice) }
        }
    }

    fun showMessage(text: String) = mutableState.update { it.copy(message = text) }

    fun messageShown() = mutableState.update { it.copy(message = null) }
}
