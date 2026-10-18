package gr.edusuite.invoicer.ui.invoices

import androidx.lifecycle.SavedStateHandle
import gr.edusuite.invoicer.ui.common.FormDraft
import androidx.lifecycle.ViewModel
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.model.sumOfMoney
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import gr.edusuite.invoicer.core.usecase.CreateInvoice
import gr.edusuite.invoicer.core.usecase.InvoiceRenderer
import gr.edusuite.invoicer.ui.common.launchCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class CreateInvoiceUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val dirty: Boolean = false,
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
    renderer: InvoiceRenderer,
    today: LocalDate = LocalDate.now(),
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val draft = FormDraft(savedState)
    private val createInvoice = CreateInvoice(lessons, invoices, renderer)
    private val mutableState = MutableStateFlow(CreateInvoiceUiState(issueDate = today))
    val state: StateFlow<CreateInvoiceUiState> = mutableState.asStateFlow()

    init {
        launchCatching(::fail) {
            val requested = if (draft.exists) draft.number("studentId") else initialStudentId
            val pickable = students.observeStudents().first().filter { !it.archived || it.id == requested }
            val groupNames = groups.observeGroups().first().associate { it.id to it.name }
            if (requested != 0L && pickable.none { it.id == requested }) {
                mutableState.update { it.copy(loading = false, notFound = true, message = "This student no longer exists") }
                return@launchCatching
            }
            val loaded = state.value.copy(loading = false, students = pickable, groupNames = groupNames)
            mutableState.value = if (draft.exists) loaded.copy(
                issueDate = LocalDate.ofEpochDay(draft.number("issueDate")),
                notes = draft.text("notes"), markPaid = draft.flag("markPaid"), dirty = true,
            ) else loaded
            if (requested != 0L) loadStudent(requested, if (draft.exists) draft.ids("selected") else null)
        }
    }

    fun selectStudent(id: Long) {
        if (state.value.loading || state.value.notFound || state.value.saving) return
        edit { it.copy(studentId = id, lessons = emptyList(), selected = emptySet()) }
        loadStudent(id)
    }

    private fun loadStudent(id: Long, restoredSelection: Set<Long>? = null) {
        mutableState.update { it.copy(studentId = id) }
        launchCatching(::fail) {
            val open = lessons.observeLessonsOfStudent(id).first()
                .filter { it.isInvoiceable }
                .sortedWith(compareBy({ it.date }, { it.startTime }))
            if (state.value.studentId != id) return@launchCatching
            val ids = open.map { it.id }.toSet()
            val updated = state.value.copy(lessons = open, selected = restoredSelection?.intersect(ids) ?: ids)
            mutableState.value = updated
            if (updated.dirty) rememberDraft(updated)
        }
    }

    fun toggle(lessonId: Long) = edit {
        it.copy(selected = if (lessonId in it.selected) it.selected - lessonId else it.selected + lessonId)
    }

    fun selectAll(all: Boolean) = edit {
        it.copy(selected = if (all) it.lessons.map { lesson -> lesson.id }.toSet() else emptySet())
    }

    fun setIssueDate(date: LocalDate) = edit { it.copy(issueDate = date) }

    fun setNotes(text: String) = edit { it.copy(notes = text) }

    fun setMarkPaid(value: Boolean) = edit { it.copy(markPaid = value) }

    fun create() {
        if (state.value.loading || state.value.notFound || state.value.saving) return
        val current = state.value
        val studentId = current.studentId ?: return
        mutableState.update { it.copy(saving = true) }
        launchCatching({ error -> mutableState.update { it.copy(saving = false, message = error) } }) {
            val invoice = createInvoice(studentId, current.selected, current.issueDate, current.notes, current.markPaid)
            draft.clear()
            mutableState.update { it.copy(saving = false, dirty = false, created = invoice) }
        }
    }

    fun showMessage(text: String) = mutableState.update { it.copy(message = text) }

    fun messageShown() = mutableState.update { it.copy(message = null) }
    private fun edit(allowedWhenLocked: Boolean = false, transform: (CreateInvoiceUiState) -> CreateInvoiceUiState) {
        val current = state.value
        if (current.loading || current.notFound || current.saving) return
        val updated = transform(current).copy(dirty = true)
        mutableState.value = updated
        rememberDraft(updated)
    }

    private fun rememberDraft(state: CreateInvoiceUiState) = draft.save(
        "studentId" to (state.studentId ?: 0L), "selected" to state.selected.toLongArray(),
        "issueDate" to state.issueDate.toEpochDay(), "notes" to state.notes, "markPaid" to state.markPaid,
    )

    private fun fail(message: String) = mutableState.update { it.copy(loading = false, notFound = it.notFound || it.loading, saving = false, message = message) }

}
