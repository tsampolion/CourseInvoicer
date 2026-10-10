package gr.eduinvoice.core.usecase

import gr.eduinvoice.core.model.DomainException
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.StudentGroup
import gr.eduinvoice.core.repository.GroupRepository
import gr.eduinvoice.core.repository.LessonRepository
import java.time.LocalDate
import java.time.LocalTime

class SaveGroup(private val groups: GroupRepository) {
    suspend operator fun invoke(group: StudentGroup): Long {
        if (group.name.isBlank()) throw DomainException("Group name is required")
        if (group.rate.amount.cents < 0) throw DomainException("Rate cannot be negative")
        return groups.saveGroup(group.copy(name = group.name.trim(), className = group.className.trim()))
    }
}

/**
 * Logs one group session: a lesson row for every attending student, billed at the group rate, and a
 * non-billable `absent` row for every absent student so attendance history is kept.
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
        studentIds: Collection<Long>,
        absentIds: Set<Long>,
        notes: String = "",
    ): List<Long> {
        val group = groups.getGroup(groupId) ?: throw DomainException("Group not found")
        validateDuration(durationMinutes)
        if (studentIds.isEmpty()) throw DomainException("Select at least one student")
        val strangers = studentIds.filterNot { it in group.memberIds }
        if (strangers.isNotEmpty()) throw DomainException("Some students are not members of ${group.name}")
        if (!studentIds.containsAll(absentIds)) throw DomainException("Absent students must be in the session")

        val fee = group.rate.feeFor(durationMinutes)
        val rows = studentIds.distinct().sorted().map { studentId ->
            Lesson(
                studentId = studentId,
                groupId = group.id,
                date = date,
                startTime = startTime,
                durationMinutes = durationMinutes,
                fee = fee,
                absent = studentId in absentIds,
                notes = notes.trim(),
            )
        }
        return lessons.insertLessons(rows)
    }
}
