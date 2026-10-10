package gr.eduinvoice.core.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * One student's attendance at a lesson. Group lessons create one row per member.
 *
 * [fee] is snapshotted when the lesson is logged so later rate changes never rewrite history.
 */
data class Lesson(
    val id: Long = 0,
    val studentId: Long,
    val groupId: Long? = null,
    val date: LocalDate,
    val startTime: LocalTime,
    val durationMinutes: Int,
    val fee: Money,
    val absent: Boolean = false,
    val notes: String = "",
    val paidOn: LocalDate? = null,
    val invoiceId: Long? = null,
) {
    val isPaid: Boolean get() = paidOn != null

    val isInvoiced: Boolean get() = invoiceId != null

    /** What the student is charged; absences are never charged. */
    val charge: Money get() = if (absent) Money.ZERO else fee

    /** Charged but not yet paid. */
    val isOutstanding: Boolean get() = !isPaid && charge.isPositive

    /** Can be put on a new invoice. */
    val isInvoiceable: Boolean get() = !isInvoiced && !absent
}
