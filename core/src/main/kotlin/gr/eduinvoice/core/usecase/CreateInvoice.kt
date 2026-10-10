package gr.eduinvoice.core.usecase

import gr.eduinvoice.core.invoice.InvoiceNumbering
import gr.eduinvoice.core.model.DomainException
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.model.sumOfMoney
import gr.eduinvoice.core.repository.InvoiceRepository
import gr.eduinvoice.core.repository.LessonRepository
import java.time.LocalDate

class CreateInvoice(
    private val lessons: LessonRepository,
    private val invoices: InvoiceRepository,
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
        val id = invoices.createInvoice(draft, ids, paidOn = issueDate.takeIf { markPaid })
        return draft.copy(id = id)
    }
}

class VoidInvoice(private val invoices: InvoiceRepository) {
    suspend operator fun invoke(invoiceId: Long, on: LocalDate) {
        val invoice = invoices.getInvoice(invoiceId) ?: throw DomainException("Invoice not found")
        if (invoice.isVoid) throw DomainException("Invoice ${invoice.number} is already void")
        invoices.voidInvoice(invoiceId, on)
    }
}
