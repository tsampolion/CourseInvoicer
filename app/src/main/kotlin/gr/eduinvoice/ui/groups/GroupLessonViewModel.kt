package gr.eduinvoice.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.eduinvoice.core.model.Money
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.model.StudentGroup
import gr.eduinvoice.core.repository.GroupRepository
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.SettingsRepository
import gr.eduinvoice.core.repository.StudentRepository
import gr.eduinvoice.core.usecase.LogGroupLesson
import gr.eduinvoice.core.usecase.MAX_LESSON_MINUTES
import gr.eduinvoice.ui.common.launchCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Attendee(val student: Student, val present: Boolean = true)

data class GroupLessonUiState(
    val loading: Boolean = true,
    val group: StudentGroup? = null,
    val date: LocalDate,
    val time: LocalTime,
    val duration: String,
    val attendees: List<Attendee> = emptyList(),
    val notes: String = "",
    val durationError: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val done: Boolean = false,
) {
    val minutes: Int? get() = duration.toIntOrNull()?.takeIf { it in 1..MAX_LESSON_MINUTES }
    val feePerStudent: Money? get() = minutes?.let { group?.rate?.feeFor(it) }
    val presentCount: Int get() = attendees.count { it.present }
}

class GroupLessonViewModel(
    private val groupId: Long,
    private val groups: GroupRepository,
    students: StudentRepository,
    private val lessons: LessonRepository,
    settings: SettingsRepository,
    now: LocalDateTime = LocalDateTime.now(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        GroupLessonUiState(
            date = now.toLocalDate(),
            time = LocalTime.of(now.hour, 0),
            duration = settings.settings.value.defaultLessonMinutes.toString(),
        ),
    )
    val state: StateFlow<GroupLessonUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val group = groups.getGroup(groupId)
            val members = students.observeStudents().first()
                .filter { group != null && it.id in group.memberIds && !it.archived }
            mutableState.update { it.copy(loading = false, group = group, attendees = members.map(::Attendee)) }
        }
    }

    fun setDate(date: LocalDate) = mutableState.update { it.copy(date = date) }

    fun setTime(time: LocalTime) = mutableState.update { it.copy(time = time) }

    fun setDuration(text: String) = mutableState.update {
        it.copy(duration = text.filter(Char::isDigit).take(3), durationError = false)
    }

    fun setNotes(text: String) = mutableState.update { it.copy(notes = text) }

    fun togglePresent(studentId: Long) = mutableState.update { state ->
        state.copy(attendees = state.attendees.map { if (it.student.id == studentId) it.copy(present = !it.present) else it })
    }

    fun save() {
        val current = state.value
        val minutes = current.minutes
        if (minutes == null) {
            mutableState.update { it.copy(durationError = true) }
            return
        }
        mutableState.update { it.copy(saving = true) }
        launchCatching({ error -> mutableState.update { it.copy(saving = false, message = error) } }) {
            LogGroupLesson(groups, lessons)(
                groupId = groupId,
                date = current.date,
                startTime = current.time,
                durationMinutes = minutes,
                studentIds = current.attendees.map { it.student.id },
                absentIds = current.attendees.filterNot { it.present }.map { it.student.id }.toSet(),
                notes = current.notes,
            )
            mutableState.update { it.copy(saving = false, done = true) }
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }
}
