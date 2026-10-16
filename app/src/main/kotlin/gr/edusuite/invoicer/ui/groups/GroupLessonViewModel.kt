package gr.edusuite.invoicer.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.edusuite.invoicer.core.model.AbsenceCharge
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.SettingsRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import gr.edusuite.invoicer.core.usecase.Attendance
import gr.edusuite.invoicer.core.usecase.LogGroupLesson
import gr.edusuite.invoicer.core.usecase.MAX_LESSON_MINUTES
import gr.edusuite.invoicer.ui.common.launchCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** How a member took part in a group lesson (R3). */
enum class Presence {
    PRESENT,

    /** Absent and excused: recorded, not charged. */
    ABSENT,

    /** Cancelled under 24 hours before: charged the full fee. */
    LATE_CANCEL,
}

data class Attendee(val student: Student, val presence: Presence = Presence.PRESENT, val reason: String = "") {
    val present: Boolean get() = presence == Presence.PRESENT

    /** Present members and late cancellations are charged. */
    val charged: Boolean get() = presence != Presence.ABSENT
}

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
    val chargedCount: Int get() = attendees.count { it.charged }
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

    fun setPresence(studentId: Long, presence: Presence) = updateAttendee(studentId) { it.copy(presence = presence) }

    /** The reason for an absence; kept if the member is marked present again, but not saved then. */
    fun setReason(studentId: Long, text: String) = updateAttendee(studentId) { it.copy(reason = text) }

    private fun updateAttendee(studentId: Long, transform: (Attendee) -> Attendee) = mutableState.update { state ->
        state.copy(attendees = state.attendees.map { if (it.student.id == studentId) transform(it) else it })
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
                attendance = current.attendees.map { attendee ->
                    Attendance(
                        studentId = attendee.student.id,
                        absent = !attendee.present,
                        charge = if (attendee.presence == Presence.LATE_CANCEL) {
                            AbsenceCharge.LATE_CANCELLATION
                        } else {
                            AbsenceCharge.NONE
                        },
                        note = attendee.reason,
                    )
                },
                notes = current.notes,
            )
            mutableState.update { it.copy(saving = false, done = true) }
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }
}
