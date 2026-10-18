package gr.edusuite.invoicer.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.sumOfMoney
import gr.edusuite.invoicer.core.report.PeriodSummary
import gr.edusuite.invoicer.core.report.Reports
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import gr.edusuite.invoicer.ui.common.stateInCatching
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

data class Debtor(val studentId: Long, val name: String, val amount: Money, val lessons: Int)

data class DashboardUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val summary: PeriodSummary = PeriodSummary(0, 0, 0, Money.ZERO, Money.ZERO, Money.ZERO),
    val outstanding: Money = Money.ZERO,
    val debtors: List<Debtor> = emptyList(),
    val activeStudents: Int = 0,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    students: StudentRepository,
    lessons: LessonRepository,
    today: LocalDate = LocalDate.now(),
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    private fun showMessage(text: String) { message.value = text }
    fun messageShown() { message.value = null }

    private val month = MutableStateFlow(YearMonth.from(today))

    // Only the selected month's lessons are loaded; balances are aggregated by the repository.
    private val monthSummary = month.flatMapLatest { selected ->
        lessons.observeLessonsBetween(selected.atDay(1), selected.atEndOfMonth())
            .map { selected to Reports.summarize(it, selected) }
    }

    private val observedState: StateFlow<DashboardUiState> =
        combine(students.observeStudents(), lessons.observeBalances(), monthSummary) { studentList, balances, summary ->
            val names = studentList.associate { it.id to it.fullName }
            DashboardUiState(
                loading = false,
                month = summary.first,
                summary = summary.second,
                outstanding = balances.sumOfMoney { it.outstanding },
                debtors = balances.map {
                    Debtor(it.studentId, names[it.studentId].orEmpty(), it.outstanding, it.openLessons)
                },
                activeStudents = studentList.count { !it.archived },
            )
        }.stateInCatching(viewModelScope, DashboardUiState(month = YearMonth.from(today)), ::showMessage)

    // Keep error delivery alive even after the repository flow has completed with an error.
    val state: StateFlow<DashboardUiState> = combine(observedState, message) { snapshot, event ->
        snapshot.copy(message = event)
    }.stateInCatching(viewModelScope, DashboardUiState(month = YearMonth.from(today)), ::showMessage)


    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }
}
