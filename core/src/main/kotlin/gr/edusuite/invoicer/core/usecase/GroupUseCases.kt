package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.model.AbsenceCharge
import gr.edusuite.invoicer.core.model.MAX_RATE
import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import java.time.LocalDate
import java.time.LocalTime

class SaveGroup(private val groups: GroupRepository) {
    suspend operator fun invoke(group: StudentGroup): Long {
        if (group.name.isBlank()) throw DomainException("Group name is required")
        if (group.rate.amount.cents < 0) throw DomainException("Rate cannot be negative")
        if (group.rate.amount > MAX_RATE) throw DomainException("Rate cannot be more than 100,000")
        return groups.saveGroup(group.copy(name = group.name.trim(), className = group.className.trim()))
    }
}

/**
 * Deleting a group keeps its lessons but detaches them from the group. Invoiced lessons keep their group
 * (R7), so a group with invoiced lessons can only be archived.
 */
class DeleteGroup(
    private val groups: GroupRepository,
    private val lessons: LessonRepository,
) {
    suspend operator fun invoke(groupId: Long) {
        if (lessons.getLessonsOfGroup(groupId).any { it.isInvoiced }) {
            throw DomainException("Some of this group's lessons are on a statement. Archive the group instead.")
        }
        groups.deleteGroup(groupId)
    }
}

/**
 * One member's attendance at a group lesson: present, absent (excused, never charged) or absent as a
 * late cancellation (charged), with an optional reason for an absence (R3).
 */
data class Attendance(
    val studentId: Long,
    val absent: Boolean = false,
    val charge: AbsenceCharge = AbsenceCharge.NONE,
    val note: String = "",
)

/**
 * Logs one group session: a lesson row for every member in [attendance], billed at the group rate.
 * Absent members get an `absent` row so attendance history is kept; it is charged only when it is a
 * late cancellation.
 */
class LogGroupLesson(
    private val groups: GroupRepository,
    private val lessons: LessonRepository,
) {
    suspend operator fun invoke(
        groupId: Long,
        date: LocalDate,
        startTime: LocalTime,
        durationMinutes: Int,
        attendance: List<Attendance>,
        notes: String = "",
    ): List<Long> {
        val group = groups.getGroup(groupId) ?: throw DomainException("Group not found")
        validateDuration(durationMinutes)
        if (attendance.isEmpty()) throw DomainException("Select at least one student")
        val strangers = attendance.filterNot { it.studentId in group.memberIds }
        if (strangers.isNotEmpty()) throw DomainException("Some students are not members of ${group.name}")
        if (attendance.distinctBy { it.studentId }.size != attendance.size) {
            throw DomainException("Each student can be listed only once")
        }

        val fee = group.rate.feeFor(durationMinutes)
        val rows = attendance.sortedBy { it.studentId }.map { member ->
            Lesson(
                studentId = member.studentId,
                groupId = group.id,
                date = date,
                startTime = startTime,
                durationMinutes = durationMinutes,
                fee = fee,
                absent = member.absent,
                notes = notes.trim(),
                absenceCharge = if (member.absent) member.charge else AbsenceCharge.NONE,
                absenceNote = if (member.absent) member.note.trim() else "",
            )
        }
        return lessons.insertLessons(rows)
    }
}

/** Archives or restores a group without changing its history (R10). */
class ArchiveGroup(private val groups: GroupRepository) {
    suspend operator fun invoke(groupId: Long, archived: Boolean): Undoable<Unit> {
        val existing = groups.getGroup(groupId)
            ?: throw DomainException("This group no longer exists")
        if (existing.archived == archived) return Undoable(Unit, Undo {})
        groups.saveGroup(existing.copy(archived = archived))
        return Undoable(Unit, undoOnce {
            val current = groups.getGroup(groupId)
                ?: throw DomainException("This group no longer exists")
            groups.saveGroup(current.copy(archived = existing.archived))
        })
    }
}
