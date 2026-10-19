package gr.edusuite.invoicer.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.privacy.dueForErasure
import gr.edusuite.invoicer.core.privacy.isErased
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import gr.edusuite.invoicer.ui.common.stateInCatching
import java.time.LocalDate

/**
 * [overdue]: the student has a statement past the last day of its issue month (R16). [dueForErasure]: an
 * archived student who owes nothing and whose last lesson was over 12 months ago (R17).
 */
data class StudentRow(
    val student: Student,
    val outstanding: Money,
    val overdue: Boolean = false,
    val dueForErasure: Boolean = false,
)

data class StudentsUiState(
    val loading: Boolean = true,
    val showArchived: Boolean = false,
    val rows: List<StudentRow> = emptyList(),
    val totalCount: Int = 0,
    /** How many students, active or archived, have an overdue statement. */
    val overdueStudents: Int = 0,
    val message: String? = null,
)

/** The search text itself lives in the screen's text field state; this view model only filters by it. */
class StudentsViewModel(
    students: StudentRepository,
    lessons: LessonRepository,
    invoices: InvoiceRepository,
    today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    private fun showMessage(text: String) { message.value = text }
    fun messageShown() { message.value = null }

    private val query = MutableStateFlow("")
    private val showArchived = MutableStateFlow(false)
    private val filters = combine(query, showArchived) { text, archived -> text to archived }

    private val observedState: StateFlow<StudentsUiState> = combine(
        students.observeStudents(),
        lessons.observeBalances(),
        invoices.observeOverdue(today()),
        lessons.observeLastLessonDates(),
        filters,
    ) { studentList, balanceList, overdue, lastLessons, (text, archived) ->
        val balances = balanceList.associate { it.studentId to it.outstanding }
        val terms = text.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        val rows = studentList
            .filter { it.archived == archived }
            .filter { student ->
                val haystack = listOf(student.fullName, student.className, student.guardianName, student.phone)
                    .joinToString(" ")
                    .lowercase()
                terms.all { it in haystack }
            }
            .map { student ->
                val owed = balances[student.id] ?: Money.ZERO
                StudentRow(
                    student,
                    owed,
                    overdue = student.id in overdue.students,
                    dueForErasure = student.archived && !student.isErased &&
                        dueForErasure(lastLessons[student.id], owed, today()),
                )
            }
        StudentsUiState(false, archived, rows, studentList.size, overdue.students.size)
    }.stateInCatching(viewModelScope, StudentsUiState(), ::showMessage)

    // Keep error delivery alive even after the repository flow has completed with an error.
    val state: StateFlow<StudentsUiState> = combine(observedState, message) { snapshot, event ->
        snapshot.copy(message = event)
    }.stateInCatching(viewModelScope, StudentsUiState(), ::showMessage)


    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onShowArchivedChange(value: Boolean) {
        showArchived.value = value
    }
}
