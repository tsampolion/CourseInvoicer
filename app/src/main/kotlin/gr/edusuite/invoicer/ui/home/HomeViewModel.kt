package gr.edusuite.invoicer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.sumOfMoney
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The figures on the launcher's buttons. Every one comes from a repository count or aggregate, so Home never
 * loads the lesson or invoice history.
 */
data class HomeUiState(
    val loading: Boolean = true,
    val activeStudents: Int = 0,
    val activeGroups: Int = 0,
    val lessons: Int = 0,
    val uninvoicedLessons: Int = 0,
    val statements: Int = 0,
    val unpaidStatements: Int = 0,
    val outstanding: Money = Money.ZERO,
) {
    // A section counts as empty only once its figures have loaded, so the buttons do not flash dimmed.
    val studentsEmpty: Boolean get() = !loading && activeStudents == 0
    val groupsEmpty: Boolean get() = !loading && activeGroups == 0
    val lessonsEmpty: Boolean get() = !loading && lessons == 0
    val statementsEmpty: Boolean get() = !loading && statements == 0

    /** Logging a lesson needs an active student. */
    val canLogLesson: Boolean get() = !loading && activeStudents > 0
}

class HomeViewModel(
    students: StudentRepository,
    groups: GroupRepository,
    lessons: LessonRepository,
    invoices: InvoiceRepository,
) : ViewModel() {
    private data class Counts(val lessons: Int, val uninvoiced: Int, val statements: Int, val unpaid: Int)

    private val counts = combine(
        lessons.observeLessonCount(),
        lessons.observeUninvoicedCount(),
        invoices.observeInvoiceCount(),
        invoices.observeUnpaidCount(),
    ) { lessonCount, uninvoiced, statementCount, unpaid -> Counts(lessonCount, uninvoiced, statementCount, unpaid) }

    val state: StateFlow<HomeUiState> = combine(
        students.observeStudents(),
        groups.observeGroups(),
        lessons.observeBalances(),
        counts,
    ) { studentList, groupList, balances, current ->
        HomeUiState(
            loading = false,
            activeStudents = studentList.count { !it.archived },
            activeGroups = groupList.count { !it.archived },
            lessons = current.lessons,
            uninvoicedLessons = current.uninvoiced,
            statements = current.statements,
            unpaidStatements = current.unpaid,
            outstanding = balances.sumOfMoney { it.outstanding },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
