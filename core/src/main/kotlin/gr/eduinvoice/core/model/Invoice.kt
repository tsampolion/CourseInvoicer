package gr.eduinvoice.core.model

import java.time.LocalDate

/**
 * An issued invoice. Invoices are never deleted: voiding one releases its lessons but keeps the row,
 * so its number is never issued twice (a parent may already hold the PDF).
 */
data class Invoice(
    val id: Long = 0,
    val number: String,
    val studentId: Long,
    val issueDate: LocalDate,
    val total: Money,
    val notes: String = "",
    val voidedOn: LocalDate? = null,
) {
    val isVoid: Boolean get() = voidedOn != null
}
