package gr.edusuite.invoicer.core.invoice

import gr.edusuite.invoicer.core.backup.BackupFormatException
import gr.edusuite.invoicer.core.backup.Json
import gr.edusuite.invoicer.core.model.InvoiceDocument

/**
 * JSON form of an issued [InvoiceDocument], stored with the invoice and in backups.
 * [InvoiceDocument.paid] is live, so it is never stored and always decodes as false.
 * `title`, `disclaimer` and a line's `note` (2.0.0, R14) are written only when set, and documents stored
 * without them decode with nulls, exactly as they were issued.
 */
object InvoiceDocumentCodec {
    fun encode(document: InvoiceDocument): String = Json.write(toJson(document))

    fun decode(text: String): InvoiceDocument =
        fromJson(Json.parse(text) as? Map<*, *> ?: throw BackupFormatException("Invalid invoice document"))

    internal fun toJson(document: InvoiceDocument): Map<String, Any?> = buildMap {
        put("number", document.number)
        put("issueDate", document.issueDate)
        document.title?.let { put("title", it) }
        put("seller", document.seller)
        put("billTo", document.billTo)
        put(
            "lines",
            document.lines.map { line ->
                buildMap {
                    put("date", line.date)
                    put("description", line.description)
                    put("duration", line.duration)
                    put("amount", line.amount)
                    line.note?.let { put("note", it) }
                }
            },
        )
        put("total", document.total)
        put("notes", document.notes)
        document.disclaimer?.let { put("disclaimer", it) }
    }

    internal fun fromJson(json: Map<*, *>): InvoiceDocument = InvoiceDocument(
        number = json.text("number"),
        issueDate = json.text("issueDate"),
        seller = json.texts("seller"),
        billTo = json.texts("billTo"),
        lines = json.items("lines").map {
            InvoiceDocument.Line(
                date = it.text("date"),
                description = it.text("description"),
                duration = it.text("duration"),
                amount = it.text("amount"),
                note = it.optionalText("note"),
            )
        },
        total = json.text("total"),
        paid = false,
        notes = json.texts("notes"),
        title = json.optionalText("title"),
        disclaimer = json.optionalText("disclaimer"),
    )

    private fun Map<*, *>.text(key: String): String = get(key) as? String ?: invalid(key)

    /** Absent or null for documents issued before the field existed. */
    private fun Map<*, *>.optionalText(key: String): String? = get(key)?.let { it as? String ?: invalid(key) }

    private fun Map<*, *>.list(key: String): List<*> = get(key) as? List<*> ?: invalid(key)

    private fun Map<*, *>.texts(key: String): List<String> = list(key).map { it as? String ?: invalid(key) }

    private fun Map<*, *>.items(key: String): List<Map<*, *>> = list(key).map { it as? Map<*, *> ?: invalid(key) }

    private fun invalid(key: String): Nothing = throw BackupFormatException("Invalid invoice document field '$key'")
}
