package gr.edusuite.invoicer.core.privacy

import gr.edusuite.invoicer.core.model.Money
import java.time.LocalDate

/** How long a student's data is kept after their last lesson once everything is paid (R17). */
const val RETENTION_MONTHS = 12L

/**
 * True when a student's data is due for erasure (R17): they have had lessons, owe nothing, and [today] is
 * more than [RETENTION_MONTHS] after their [lastLesson].
 */
fun dueForErasure(lastLesson: LocalDate?, outstanding: Money, today: LocalDate): Boolean =
    lastLesson != null && !outstanding.isPositive && today.isAfter(lastLesson.plusMonths(RETENTION_MONTHS))
