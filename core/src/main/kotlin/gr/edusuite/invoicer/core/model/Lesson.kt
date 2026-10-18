package gr.edusuite.invoicer.core.model

import java.time.LocalDate
import java.time.LocalTime

/** Whether a missed lesson is charged (R3). */
enum class AbsenceCharge {
    /** An excused absence: recorded, never charged. */
    NONE,

    /** Cancelled too late (under 24 hours): charged the full fee. */
    LATE_CANCELLATION,
}

/**
 * One student's attendance at a lesson. Group lessons create one row per member.
 *
 * [fee] is snapshotted when the lesson is logged so later rate changes never rewrite history.
 * [absenceCharge] and [absenceNote] only mean something while [absent] is true.
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
    val absenceCharge: AbsenceCharge = AbsenceCharge.NONE,
    val absenceNote: String = "",
) {
    val isPaid: Boolean get() = paidOn != null

    val isInvoiced: Boolean get() = invoiceId != null

    /** An absence that is not charged. */
    val isExcused: Boolean get() = absent && absenceCharge == AbsenceCharge.NONE

    /** A late cancellation, charged like a lesson that was held. */
    val isLateCancellation: Boolean get() = absent && absenceCharge == AbsenceCharge.LATE_CANCELLATION

    /** What the student is charged: the fee, except for excused absences, which are never charged. */
    val charge: Money get() = chargeFor(fee, absent, absenceCharge)

    /** Charged but not yet paid. */
    val isOutstanding: Boolean get() = !isPaid && charge.isPositive

    /** Can be put on a new statement. Excused absences can too, as 0.00 lines (R4). */
    val isInvoiceable: Boolean get() = !isInvoiced
}

/** Charge for a saved lesson or an unsaved form (R3). */
fun chargeFor(fee: Money, absent: Boolean, absenceCharge: AbsenceCharge): Money =
    if (absent && absenceCharge == AbsenceCharge.NONE) Money.ZERO else fee
