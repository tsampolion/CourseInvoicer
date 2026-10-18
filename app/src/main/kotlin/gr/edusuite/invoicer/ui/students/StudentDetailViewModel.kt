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
import gr.edusuite.invoicer.core.usecase.ArchiveStudent
import gr.edusuite.invoicer.core.usecase.DeleteStudent
import gr.edusuite.invoicer.core.usecase.MarkPaid
import gr.edusuite.invoicer.core.usecase.RecordPayment
import gr.edusuite.invoicer.ui.common.Messages
import gr.edusuite.invoicer.ui.common.launchCatching
import gr.edusuite.invoicer.ui.common.plural
import gr.edusuite.invoicer.ui.common.stateInCatching
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

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
    private val messages: Messages = Messages(),
) : ViewModel() {
    private data class Events(val message: String? = null, val deleted: Boolean = false)

    private val events = MutableStateFlow(Events())
    private val markPaid = MarkPaid(lessons)
    private val archiveStudent = ArchiveStudent(students)
    private val recordPaymentUseCase = RecordPayment(lessons)
    private val deleteStudent = DeleteStudent(students, lessons, invoices)

    private val observedState: StateFlow<StudentDetailUiState> = combine(
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
    }.stateInCatching(viewModelScope, StudentDetailUiState(), ::showMessage)

    // Keep error delivery alive even after the repository flow has completed with an error.
    val state: StateFlow<StudentDetailUiState> = combine(observedState, events) { snapshot, event ->
        snapshot.copy(message = event.message, deleted = event.deleted)
    }.stateInCatching(viewModelScope, StudentDetailUiState(), ::showMessage)


    fun recordPayment() = launchCatching(::showMessage) {
        val action = recordPaymentUseCase(studentId, today())
        val result = action.result
        val formats = Formats(Locale.getDefault())
        messages.postUndo("Recorded ${formats.money(result.amount)} for ${plural(result.lessonCount, "lesson")}", action)
    }

    fun setPaid(lesson: Lesson, paid: Boolean) = launchCatching(::showMessage) {
        val action = markPaid(listOf(lesson.id), if (paid) today() else null)
        messages.postUndo("Marked ${plural(action.result, "lesson")} ${if (paid) "paid" else "unpaid"}", action)
    }

    fun setArchived(archived: Boolean) = launchCatching(::showMessage) {
        val action = archiveStudent(studentId, archived)
        messages.postUndo(if (archived) "Student archived" else "Student restored", action)
    }

    fun delete() = launchCatching(::showMessage) {
        deleteStudent(studentId)
        events.update { it.copy(deleted = true) }
    }

    fun messageShown() = events.update { it.copy(message = null) }

    private fun showMessage(message: String) = events.update { it.copy(message = message) }
}
