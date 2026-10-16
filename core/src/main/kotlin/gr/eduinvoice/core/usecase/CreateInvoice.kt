package gr.eduinvoice.core.usecase

import gr.eduinvoice.core.format.Formats
import gr.eduinvoice.core.invoice.InvoiceDocumentBuilder
import gr.eduinvoice.core.invoice.InvoiceNumbering
import gr.eduinvoice.core.model.DomainException
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.model.InvoiceDocument
import gr.eduinvoice.core.model.InvoicePayment
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.sumOfMoney
import gr.eduinvoice.core.repository.GroupRepository
import gr.eduinvoice.core.repository.InvoiceRepository
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.SettingsRepository
import gr.eduinvoice.core.repository.StudentRepository
import java.time.LocalDate
import java.util.Locale

/** Formats an invoice's document from the current student, group names and settings. */
class InvoiceRenderer(
    private val students: StudentRepository,
    private val groups: GroupRepository,
    private val settings: SettingsRepository,
    private val locale: () -> Locale = Locale::getDefault,
) {
    /** The returned document has `paid = false`: payment status is live, never part of the stored document. */
    suspend fun render(invoice: Invoice, lessons: List<Lesson>): InvoiceDocument {
        val student = students.getStudent(invoice.studentId) ?: throw DomainException("Student not found")
        val groupNames = lessons.mapNotNull { it.groupId }.distinct()
            .mapNotNull { id -> groups.getGroup(id)?.let { id to it.name } }
            .toMap()
        val current = settings.settings.value
        val formats = Formats(locale(), current.currencyCode)
        return InvoiceDocumentBuilder.build(invoice, student, lessons, groupNames, current, formats).copy(paid = false)
    }
}

class CreateInvoice(
    private val lessons: LessonRepository,
    private val invoices: InvoiceRepository,
    private val renderer: InvoiceRenderer,
) {
    suspend operator fun invoke(
        studentId: Long,
        lessonIds: Collection<Long>,
        issueDate: LocalDate,
        notes: String = "",
        markPaid: Boolean = false,
    ): Invoice {
        val ids = lessonIds.toSet()
        if (ids.isEmpty()) throw DomainException("Select at least one lesson")
        val selected = lessons.getLessons(ids)
        if (selected.size != ids.size) throw DomainException("Some lessons no longer exist")
        if (selected.any { it.studentId != studentId }) throw DomainException("All lessons must belong to the same student")
        if (selected.any { it.isInvoiced }) throw DomainException("Some lessons are already on an invoice")
        if (selected.any { it.absent }) throw DomainException("Absences cannot be invoiced")

        val draft = Invoice(
            number = InvoiceNumbering.next(issueDate.year, invoices.invoiceNumbers()),
            studentId = studentId,
            issueDate = issueDate,
            total = selected.sumOfMoney { it.charge },
            notes = notes.trim(),
        )
        // The document is frozen now, as issued (D7).
        val issued = draft.copy(document = renderer.render(draft, selected))
        val id = invoices.createInvoice(issued, ids, paidOn = issueDate.takeIf { markPaid })
        return issued.copy(id = id)
    }
}

/** The document to share for an invoice: as issued, with its current payment status. */
class GetInvoiceDocument(
    private val lessons: LessonRepository,
    private val invoices: InvoiceRepository,
    private val renderer: InvoiceRenderer,
) {
    suspend operator fun invoke(invoiceId: Long): InvoiceDocument {
        val invoice = invoices.getInvoice(invoiceId) ?: throw DomainException("Invoice not found")
        if (invoice.isVoid) throw DomainException("Invoice ${invoice.number} is void and cannot be shared")
        val linked = lessons.getLessonsOfInvoice(invoiceId)
        // Invoices issued before documents were stored (schema 2) are frozen the first time they are shared.
        val document = invoice.document
            ?: renderer.render(invoice, linked).also { invoices.setDocument(invoiceId, it) }
        return document.copy(paid = InvoicePayment.of(linked).isPaid)
    }
}

class VoidInvoice(private val invoices: InvoiceRepository) {
    suspend operator fun invoke(invoiceId: Long, on: LocalDate) {
        val invoice = invoices.getInvoice(invoiceId) ?: throw DomainException("Invoice not found")
        if (invoice.isVoid) throw DomainException("Invoice ${invoice.number} is already void")
        invoices.voidInvoice(invoiceId, on)
    }
}
