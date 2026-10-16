package gr.edusuite.invoicer.core.invoice

import gr.edusuite.invoicer.core.format.Formats
import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.InvoiceDocument
import gr.edusuite.invoicer.core.model.InvoicePayment
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Student

/** Formats an invoice's content from the current data; see [InvoiceDocument] for when it is frozen. */
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
            paid = InvoicePayment.of(lessons).isPaid,
            notes = notes,
        )
    }
}
