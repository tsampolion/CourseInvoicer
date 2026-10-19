package gr.edusuite.invoicer.core.model

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** When a statement issued on [issueDate] should be paid by: the last day of its issue month (R16). */
fun dueDate(issueDate: LocalDate): LocalDate = issueDate.with(TemporalAdjusters.lastDayOfMonth())

/**
 * A statement is overdue (R16) when it is not void, still has an unpaid charged lesson
 * ([InvoicePayment.isPaid]) and [today] is after its [dueDate]. With no linked lessons ([payment] null)
 * there is nothing to pay, as in `InvoiceRepository.observeUnpaidCount`.
 */
fun isOverdue(invoice: Invoice, payment: InvoicePayment?, today: LocalDate): Boolean =
    !invoice.isVoid && payment != null && !payment.isPaid && today.isAfter(dueDate(invoice.issueDate))

/** The overdue statements: how many, and whose. */
data class OverdueSummary(val statements: Int = 0, val students: Set<Long> = emptySet())
