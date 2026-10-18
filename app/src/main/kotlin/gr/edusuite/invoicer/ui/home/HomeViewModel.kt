package gr.edusuite.invoicer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.sumOfMoney
import gr.edusuite.invoicer.core.report.InvoiceReminder
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.ReminderStateRepository
import gr.edusuite.invoicer.core.repository.SettingsRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import gr.edusuite.invoicer.ui.common.stateInCatching
import gr.edusuite.invoicer.ui.common.launchCatching
import java.time.LocalDate
import java.time.YearMonth

/** The month-end reminder (R15): [month] still has [lessons] lessons for [students] students on no statement. */
data class ReminderPrompt(val month: YearMonth, val lessons: Int, val students: Int)

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
    /** The month-end reminder to show, or null. */
    val reminder: ReminderPrompt? = null,
    val message: String? = null,
) {
    // A section counts as empty only once its figures have loaded, so the buttons do not flash dimmed.
    val studentsEmpty: Boolean get() = !loading && activeStudents == 0
    val groupsEmpty: Boolean get() = !loading && activeGroups == 0
    val lessonsEmpty: Boolean get() = !loading && lessons == 0
    val statementsEmpty: Boolean get() = !loading && statements == 0

    /** Logging a lesson needs an active student. */
    val canLogLesson: Boolean get() = !loading && activeStudents > 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    students: StudentRepository,
    groups: GroupRepository,
    lessons: LessonRepository,
    invoices: InvoiceRepository,
    settings: SettingsRepository,
    private val reminderState: ReminderStateRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    private fun showMessage(text: String) { message.value = text }
    fun messageShown() { message.value = null }

    private data class Counts(val lessons: Int, val uninvoiced: Int, val statements: Int, val unpaid: Int)

    private val counts = combine(
        lessons.observeLessonCount(),
        lessons.observeUninvoicedCount(),
        invoices.observeInvoiceCount(),
        invoices.observeUnpaidCount(),
    ) { lessonCount, uninvoiced, statementCount, unpaid -> Counts(lessonCount, uninvoiced, statementCount, unpaid) }

    /**
     * Set once the reminder has been answered or dismissed. Home stays at the bottom of the back stack, so the
     * reminder does not come back until the app is opened again.
     */
    private val reminderDismissed = MutableStateFlow(false)

    // The month the schedule says is due, then whether it still has lessons on no statement.
    private val reminder: Flow<ReminderPrompt?> = combine(
        settings.settings,
        reminderState.state,
        reminderDismissed,
    ) { current, answers, dismissed ->
        if (dismissed) null else InvoiceReminder.dueMonth(today(), current.reminders, answers)
    }.distinctUntilChanged().flatMapLatest { month ->
        if (month == null) {
            flowOf(null)
        } else {
            lessons.observeUninvoicedSummary(month.atDay(1), month.atEndOfMonth()).map { open ->
                if (open.lessons > 0) ReminderPrompt(month, open.lessons, open.students) else null
            }
        }
    }

    private val observedState: StateFlow<HomeUiState> = combine(
        students.observeStudents(),
        groups.observeGroups(),
        lessons.observeBalances(),
        counts,
        reminder,
    ) { studentList, groupList, balances, current, prompt ->
        HomeUiState(
            loading = false,
            activeStudents = studentList.count { !it.archived },
            activeGroups = groupList.count { !it.archived },
            lessons = current.lessons,
            uninvoicedLessons = current.uninvoiced,
            statements = current.statements,
            unpaidStatements = current.unpaid,
            outstanding = balances.sumOfMoney { it.outstanding },
            reminder = prompt,
        )
    }.stateInCatching(viewModelScope, HomeUiState(), ::showMessage)

    // Keep error delivery alive even after the repository flow has completed with an error.
    val state: StateFlow<HomeUiState> = combine(observedState, message) { snapshot, event ->
        snapshot.copy(message = event)
    }.stateInCatching(viewModelScope, HomeUiState(), ::showMessage)


    /** "Remind me tomorrow": nothing until the next day. */
    fun remindTomorrow() {
        if (state.value.reminder == null) return
        reminderDismissed.value = true
        val tomorrow = today().plusDays(1)
        launchCatching(::showMessage) { reminderState.update { it.copy(snoozedUntil = tomorrow) } }
    }

    /** "Done for this month": the reminded month is not reminded again. */
    fun doneForMonth() {
        val month = state.value.reminder?.month ?: return
        reminderDismissed.value = true
        launchCatching(::showMessage) { reminderState.update { it.copy(doneFor = month) } }
    }

    /** Hides the reminder without answering it (Back, or "Create statements"); the next app start asks again. */
    fun dismissReminder() {
        reminderDismissed.value = true
    }
}
