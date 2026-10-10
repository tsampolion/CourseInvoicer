package gr.eduinvoice.core.invoice

import gr.eduinvoice.core.format.Formats
import gr.eduinvoice.core.model.AppSettings
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.Student

/** Fully formatted invoice content. The Android PDF renderer only lays it out. */
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

object InvoiceDocumentBuilder {
    fun build(
        invoice: Invoice,
        student: Student,
        lessons: List<Lesson>,
        groupNames: Map<Long, String>,
        settings: AppSettings,
        formats: Formats,
    ): InvoiceDocument {
        val business = settings.business
        val seller = listOf(
            business.name,
            business.address,
            business.taxId.takeIf { it.isNotBlank() }?.let { "Tax ID: $it" }.orEmpty(),
            listOf(business.phone, business.email).filter { it.isNotBlank() }.joinToString(" · "),
        ).flatMap { it.lines() }.filter { it.isNotBlank() }

        val billTo = listOf(
            student.fullName,
            student.guardianName.takeIf { it.isNotBlank() }?.let { "c/o $it" }.orEmpty(),
            student.phone,
            student.email,
        ).filter { it.isNotBlank() }

        val lines = lessons.sortedWith(compareBy({ it.date }, { it.startTime })).map { lesson ->
            val kind = lesson.groupId?.let { groupNames[it] }?.let { "Group lesson · $it" } ?: "Lesson"
            val description = listOf(kind, lesson.notes.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
            InvoiceDocument.Line(
                date = formats.date(lesson.date),
                description = description,
                duration = formats.duration(lesson.durationMinutes),
                amount = formats.money(lesson.charge),
            )
        }

        val notes = buildList {
            invoice.notes.takeIf { it.isNotBlank() }?.let(::add)
            business.iban.takeIf { it.isNotBlank() }?.let { add("Payment by bank transfer to IBAN $it") }
            settings.invoiceFooter.takeIf { it.isNotBlank() }?.let(::add)
        }

        return InvoiceDocument(
            number = invoice.number,
            issueDate = formats.date(invoice.issueDate),
            seller = seller,
            billTo = billTo,
            lines = lines,
            total = formats.money(invoice.total),
            paid = lessons.isNotEmpty() && lessons.all { it.isPaid },
            notes = notes,
        )
    }
}
