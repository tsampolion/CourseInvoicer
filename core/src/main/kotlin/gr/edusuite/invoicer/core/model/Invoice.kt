package gr.edusuite.invoicer.core.model

import java.time.LocalDate

/**
 * An issued invoice. Invoices are never deleted: voiding one releases its lessons but keeps the row,
 * so its number is never issued twice (a parent may already hold the PDF).
 *
 * [document] is the content as issued. It is null only for invoices created before documents were
 * stored (schema 2), until they are first shared.
 */
data class Invoice(
    val id: Long = 0,
    val number: String,
    val studentId: Long,
    val issueDate: LocalDate,
    val total: Money,
    val notes: String = "",
    val voidedOn: LocalDate? = null,
    val document: InvoiceDocument? = null,
) {
    val isVoid: Boolean get() = voidedOn != null
}

/** How far the lessons of one invoice are paid. The single definition of a paid invoice. */
data class InvoicePayment(val lessonCount: Int, val unpaidCount: Int) {
    /** An invoice is paid once it has lessons and every one of them is paid. */
    val isPaid: Boolean get() = lessonCount > 0 && unpaidCount == 0

    companion object {
        fun of(lessons: Collection<Lesson>) = InvoicePayment(lessons.size, lessons.count { !it.isPaid })
    }
}
