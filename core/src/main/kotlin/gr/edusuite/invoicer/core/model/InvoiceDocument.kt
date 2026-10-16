package gr.edusuite.invoicer.core.model

/**
 * Fully formatted statement content. It is frozen when the statement is issued, so later changes to the
 * settings, the student, group names or lesson notes never change a document a parent may already hold
 * (R12, D10). Only [paid] is live: it is filled in from the current payments each time the statement is shared.
 * The Android PDF renderer only lays it out.
 *
 * [title] and [disclaimer] are null in documents issued before statements (2.0.0, R14); those keep
 * rendering as they were issued.
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
    val title: String? = null,
    val disclaimer: String? = null,
) {
    /** [note] explains an absence line, e.g. why it is or is not charged; null for lessons that were held. */
    data class Line(
        val date: String,
        val description: String,
        val duration: String,
        val amount: String,
        val note: String? = null,
    )
}
