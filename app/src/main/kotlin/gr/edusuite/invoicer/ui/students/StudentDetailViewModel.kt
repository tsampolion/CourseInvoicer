package gr.edusuite.invoicer.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.edusuite.invoicer.core.format.Formats
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.report.Reports
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.SettingsRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import gr.edusuite.invoicer.core.usecase.DeleteStudent
import gr.edusuite.invoicer.core.usecase.RecordPayment
import gr.edusuite.invoicer.ui.common.launchCatching
import gr.edusuite.invoicer.ui.common.plural
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.util.Locale

data class StudentDetailUiState(
    val loading: Boolean = true,
    val student: Student? = null,
    val lessons: List<Lesson> = emptyList(),
    val groupNames: Map<Long, String> = emptyMap(),
    val invoices: List<Invoice> = emptyList(),
    val outstanding: Money = Money.ZERO,
    val openLessons: Int = 0,
    val invoiceableLessons: Int = 0,
    val message: String? = null,
    val deleted: Boolean = false,
)

class StudentDetailViewModel(
    private val studentId: Long,
    private val students: StudentRepository,
    groups: GroupRepository,
    private val lessons: LessonRepository,
    invoices: InvoiceRepository,
    private val settings: SettingsRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private data class Events(val message: String? = null, val deleted: Boolean = false)

    private val events = MutableStateFlow(Events())
    private val recordPaymentUseCase = RecordPayment(lessons)
    private val deleteStudent = DeleteStudent(students, lessons, invoices)

    val state: StateFlow<StudentDetailUiState> = combine(
        students.observeStudent(studentId),
        lessons.observeLessonsOfStudent(studentId),
        invoices.observeInvoices(),
        groups.observeGroups(),
        events,
    ) { student, lessonList, invoiceList, groupList, event ->
        StudentDetailUiState(
            loading = false,
            student = student,
            lessons = lessonList,
            groupNames = groupList.associate { it.id to it.name },
            invoices = invoiceList.filter { it.studentId == studentId },
            outstanding = Reports.outstanding(lessonList),
            openLessons = lessonList.count { it.isOutstanding },
            invoiceableLessons = lessonList.count { it.isInvoiceable },
            message = event.message,
            deleted = event.deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentDetailUiState())

    fun recordPayment() = launchCatching(::showMessage) {
        val result = recordPaymentUseCase(studentId, today())
        val formats = Formats(Locale.getDefault())
        showMessage("Recorded ${formats.money(result.amount)} for ${plural(result.lessonCount, "lesson")}")
    }

    fun setPaid(lesson: Lesson, paid: Boolean) = launchCatching(::showMessage) {
        lessons.setPaid(listOf(lesson.id), if (paid) today() else null)
    }

    fun setArchived(archived: Boolean) = launchCatching(::showMessage) {
        val student = students.getStudent(studentId) ?: return@launchCatching
        students.saveStudent(student.copy(archived = archived))
        showMessage(if (archived) "Student archived" else "Student restored")
    }

    fun delete() = launchCatching(::showMessage) {
        deleteStudent(studentId)
        events.update { it.copy(deleted = true) }
    }

    fun messageShown() = events.update { it.copy(message = null) }

    private fun showMessage(message: String) = events.update { it.copy(message = message) }
}
