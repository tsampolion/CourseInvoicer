package gr.eduinvoice.core.invoice

import gr.eduinvoice.core.backup.BackupFormatException
import gr.eduinvoice.core.backup.Json
import gr.eduinvoice.core.model.InvoiceDocument

/**
 * JSON form of an issued [InvoiceDocument], stored with the invoice and in backups.
 * [InvoiceDocument.paid] is live, so it is never stored and always decodes as false.
 */
object InvoiceDocumentCodec {
    fun encode(document: InvoiceDocument): String = Json.write(toJson(document))

    fun decode(text: String): InvoiceDocument =
        fromJson(Json.parse(text) as? Map<*, *> ?: throw BackupFormatException("Invalid invoice document"))

    internal fun toJson(document: InvoiceDocument): Map<String, Any?> = mapOf(
        "number" to document.number,
        "issueDate" to document.issueDate,
        "seller" to document.seller,
        "billTo" to document.billTo,
        "lines" to document.lines.map {
            mapOf("date" to it.date, "description" to it.description, "duration" to it.duration, "amount" to it.amount)
        },
        "total" to document.total,
        "notes" to document.notes,
    )

    internal fun fromJson(json: Map<*, *>): InvoiceDocument = InvoiceDocument(
        number = json.text("number"),
        issueDate = json.text("issueDate"),
        seller = json.texts("seller"),
        billTo = json.texts("billTo"),
        lines = json.items("lines").map {
            InvoiceDocument.Line(it.text("date"), it.text("description"), it.text("duration"), it.text("amount"))
        },
        total = json.text("total"),
        paid = false,
        notes = json.texts("notes"),
    )

    private fun Map<*, *>.text(key: String): String = get(key) as? String ?: invalid(key)

    private fun Map<*, *>.list(key: String): List<*> = get(key) as? List<*> ?: invalid(key)

    private fun Map<*, *>.texts(key: String): List<String> = list(key).map { it as? String ?: invalid(key) }

    private fun Map<*, *>.items(key: String): List<Map<*, *>> = list(key).map { it as? Map<*, *> ?: invalid(key) }

    private fun invalid(key: String): Nothing = throw BackupFormatException("Invalid invoice document field '$key'")
}
