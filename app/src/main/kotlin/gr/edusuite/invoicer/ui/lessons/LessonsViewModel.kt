package gr.edusuite.invoicer.ui.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.sumOfMoney
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import gr.edusuite.invoicer.core.usecase.RescheduleLessons
import gr.edusuite.invoicer.ui.common.launchCatching
import gr.edusuite.invoicer.ui.common.plural
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime
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
    /** Lessons picked with a long press; non-empty means the screen is in selection mode. */
    val selected: Set<Long> = emptySet(),
) {
    private val selectedLessons: List<Lesson> get() = items.map { it.lesson }.filter { it.id in selected }

    /** Reschedule needs every selected lesson to be unpaid and not invoiced (see RescheduleLessons). */
    val canReschedule: Boolean get() = selectedLessons.isNotEmpty() && selectedLessons.none { it.isPaid || it.isInvoiced }

    val canMarkPaid: Boolean get() = selectedLessons.any { it.isOutstanding }

    /** Date and time to pre-fill the reschedule dialog with: the earliest selected lesson. */
    val firstSelected: Lesson? get() = selectedLessons.minWithOrNull(compareBy({ it.date }, { it.startTime }))
}

private data class Controls(
    val month: YearMonth,
    val filter: LessonFilter,
    val message: String?,
    val selected: Set<Long>,
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
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val rescheduleLessons = RescheduleLessons(lessons)

    private val monthLessons = month.flatMapLatest { lessons.observeLessonsBetween(it.atDay(1), it.atEndOfMonth()) }

    val state: StateFlow<LessonsUiState> = combine(
        combine(month, filter, message, selection) { m, f, msg, sel -> Controls(m, f, msg, sel) },
        monthLessons,
        students.observeStudents(),
        groups.observeGroups(),
    ) { controls, lessonList, studentList, groupList ->
        val (selectedMonth, selectedFilter, currentMessage, selectedIds) = controls
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
            // Lessons that left the list (filtered out or deleted) silently leave the selection.
            selected = selectedIds.filterTo(mutableSetOf()) { id -> visible.any { it.id == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LessonsUiState(month = YearMonth.from(today())))

    fun previousMonth() {
        clearSelection()
        month.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        clearSelection()
        month.update { it.plusMonths(1) }
    }

    fun setFilter(value: LessonFilter) {
        clearSelection()
        filter.value = value
    }

    fun toggleSelection(lessonId: Long) = selection.update { if (lessonId in it) it - lessonId else it + lessonId }

    fun clearSelection() {
        selection.value = emptySet()
    }

    /** Marks the selected lessons that are still owed as paid today. */
    fun markSelectedPaid() {
        val owed = state.value.items.map { it.lesson }.filter { it.id in state.value.selected && it.isOutstanding }
        launchCatching({ message.value = it }) {
            lessons.setPaid(owed.map { it.id }, today())
            clearSelection()
            message.value = "Marked ${plural(owed.size, "lesson")} as paid"
        }
    }

    fun rescheduleSelected(date: LocalDate, startTime: LocalTime) {
        val ids = state.value.selected
        launchCatching({ message.value = it }) {
            val moved = rescheduleLessons(ids, date, startTime)
            clearSelection()
            message.value = "Moved ${plural(moved, "lesson")}"
        }
    }

    fun setPaid(lesson: Lesson, paid: Boolean) = launchCatching({ message.value = it }) {
        lessons.setPaid(listOf(lesson.id), if (paid) today() else null)
    }

    fun messageShown() {
        message.value = null
    }
}
