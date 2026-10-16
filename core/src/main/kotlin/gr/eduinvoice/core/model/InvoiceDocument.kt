package gr.eduinvoice.core.model

/**
 * Fully formatted invoice content. It is frozen when the invoice is issued, so later changes to the
 * settings, the student, group names or lesson notes never change a document a parent may already hold
 * (R12, D10). Only [paid] is live: it is filled in from the current payments each time the invoice is shared.
 * The Android PDF renderer only lays it out.
 */
data class InvoiceDocument(
    val number: String,
    val issueDate: String,
    val seller: List<String>,
    val billTo: List<String>,
    val lines: List<Line>,
    val total: String,
    val paid: Boolean,
    val notes: List<String>,
) {
    data class Line(val date: String, val description: String, val duration: String, val amount: String)
}
