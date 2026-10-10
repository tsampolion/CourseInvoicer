package gr.eduinvoice.ui.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.Money
import gr.eduinvoice.core.model.sumOfMoney
import gr.eduinvoice.core.repository.GroupRepository
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.StudentRepository
import gr.eduinvoice.ui.common.launchCatching
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

enum class LessonFilter(val label: String) {
    ALL("All"),
    UNPAID("Unpaid"),
    NOT_INVOICED("Not invoiced"),
}

data class LessonItem(val lesson: Lesson, val title: String)

data class LessonsUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val filter: LessonFilter = LessonFilter.ALL,
    val items: List<LessonItem> = emptyList(),
    val total: Money = Money.ZERO,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LessonsViewModel(
    private val lessons: LessonRepository,
    students: StudentRepository,
    groups: GroupRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(today()))
    private val filter = MutableStateFlow(LessonFilter.ALL)
    private val message = MutableStateFlow<String?>(null)

    private val monthLessons = month.flatMapLatest { lessons.observeLessonsBetween(it.atDay(1), it.atEndOfMonth()) }

    val state: StateFlow<LessonsUiState> = combine(
        combine(month, filter, message) { m, f, msg -> Triple(m, f, msg) },
        monthLessons,
        students.observeStudents(),
        groups.observeGroups(),
    ) { (selectedMonth, selectedFilter, currentMessage), lessonList, studentList, groupList ->
        val names = studentList.associate { it.id to it.fullName }
        val groupNames = groupList.associate { it.id to it.name }
        val visible = lessonList.filter {
            when (selectedFilter) {
                LessonFilter.ALL -> true
                LessonFilter.UNPAID -> it.isOutstanding
                LessonFilter.NOT_INVOICED -> it.isInvoiceable
            }
        }
        LessonsUiState(
            loading = false,
            month = selectedMonth,
            filter = selectedFilter,
            items = visible.map { lesson ->
                val group = lesson.groupId?.let { groupNames[it] }?.let { " · $it" }.orEmpty()
                LessonItem(lesson, names[lesson.studentId].orEmpty() + group)
            },
            total = visible.sumOfMoney { it.charge },
            message = currentMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LessonsUiState(month = YearMonth.from(today())))

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }

    fun setFilter(value: LessonFilter) {
        filter.value = value
    }

    fun setPaid(lesson: Lesson, paid: Boolean) = launchCatching({ message.value = it }) {
        lessons.setPaid(listOf(lesson.id), if (paid) today() else null)
    }

    fun messageShown() {
        message.value = null
    }
}
