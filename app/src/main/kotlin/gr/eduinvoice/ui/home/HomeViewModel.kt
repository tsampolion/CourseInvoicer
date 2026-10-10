package gr.eduinvoice.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.eduinvoice.core.model.Money
import gr.eduinvoice.core.report.PeriodSummary
import gr.eduinvoice.core.report.Reports
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

data class Debtor(val studentId: Long, val name: String, val amount: Money, val lessons: Int)

data class HomeUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val summary: PeriodSummary = PeriodSummary(0, 0, 0, Money.ZERO, Money.ZERO, Money.ZERO),
    val outstanding: Money = Money.ZERO,
    val debtors: List<Debtor> = emptyList(),
    val activeStudents: Int = 0,
)

class HomeViewModel(
    students: StudentRepository,
    lessons: LessonRepository,
    today: LocalDate = LocalDate.now(),
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(today))

    val state: StateFlow<HomeUiState> =
        combine(students.observeStudents(), lessons.observeLessons(), month) { studentList, lessonList, selected ->
            val names = studentList.associate { it.id to it.fullName }
            HomeUiState(
                loading = false,
                month = selected,
                summary = Reports.summarize(lessonList, selected),
                outstanding = Reports.outstanding(lessonList),
                debtors = Reports.balances(lessonList).map {
                    Debtor(it.studentId, names[it.studentId].orEmpty(), it.outstanding, it.openLessons)
                },
                activeStudents = studentList.count { !it.archived },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(month = YearMonth.from(today)))

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }
}
