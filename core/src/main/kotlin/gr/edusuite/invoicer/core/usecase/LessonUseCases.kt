package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.sumOfMoney
import gr.edusuite.invoicer.core.repository.LessonRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

const val MAX_LESSON_MINUTES = 12 * 60

fun isValidDuration(minutes: Int): Boolean = minutes in 1..MAX_LESSON_MINUTES

internal fun validateDuration(minutes: Int) {
    if (!isValidDuration(minutes)) {
        throw DomainException("Duration must be between 1 and $MAX_LESSON_MINUTES minutes")
    }
}

class SaveLesson(private val lessons: LessonRepository) {
    suspend operator fun invoke(lesson: Lesson): Long {
        validateDuration(lesson.durationMinutes)
        if (lesson.fee.cents < 0) throw DomainException("Fee cannot be negative")
        // Absences are never charged (R3), so they carry no payment either.
        val normalized = lesson.copy(notes = lesson.notes.trim(), paidOn = lesson.paidOn.takeUnless { lesson.absent })
        if (lesson.id == 0L) return lessons.saveLesson(normalized.copy(invoiceId = null))

        val existing = lessons.getLesson(lesson.id) ?: throw DomainException("Lesson not found")
        // The invoice link is owned by CreateInvoice / VoidInvoice, never by the edit form.
        val updated = normalized.copy(invoiceId = existing.invoiceId)
        if (existing.isInvoiced && existing.lockedFields() != updated.lockedFields()) {
            throw DomainException("This lesson is on an invoice; void the invoice before changing it")
        }
        return lessons.saveLesson(updated)
    }

    private fun Lesson.lockedFields() = listOf(studentId, groupId, date, startTime, durationMinutes, fee, absent)
}

class DeleteLesson(private val lessons: LessonRepository) {
    suspend operator fun invoke(lessonId: Long) {
        val lesson = lessons.getLesson(lessonId) ?: return
        if (lesson.isInvoiced) throw DomainException("This lesson is on an invoice; void the invoice first")
        lessons.deleteLesson(lessonId)
    }
}

data class PaymentResult(val lessonCount: Int, val amount: Money)

/** Marks every outstanding lesson of a student as paid on [paidOn]. */
class RecordPayment(private val lessons: LessonRepository) {
    suspend operator fun invoke(studentId: Long, paidOn: LocalDate): PaymentResult {
        val open = lessons.observeLessonsOfStudent(studentId).first().filter { it.isOutstanding }
        if (open.isEmpty()) return PaymentResult(0, Money.ZERO)
        lessons.setPaid(open.map { it.id }, paidOn)
        return PaymentResult(open.size, open.sumOfMoney { it.charge })
    }
}

/**
 * Moves several lessons (for example a whole group session) to a new date and start time in one step.
 * Only unpaid, uninvoiced lessons can move; duration and fee are kept.
 */
class RescheduleLessons(private val lessons: LessonRepository) {
    suspend operator fun invoke(lessonIds: Collection<Long>, date: LocalDate, startTime: LocalTime): Int {
        val ids = lessonIds.toSet()
        if (ids.isEmpty()) throw DomainException("Select at least one lesson")
        val selected = lessons.getLessons(ids)
        if (selected.size != ids.size) throw DomainException("Some lessons no longer exist")
        if (selected.any { it.isInvoiced }) throw DomainException("Invoiced lessons cannot be rescheduled")
        if (selected.any { it.isPaid }) throw DomainException("Paid lessons cannot be rescheduled")
        lessons.updateLessons(selected.map { it.copy(date = date, startTime = startTime) })
        return selected.size
    }
}
