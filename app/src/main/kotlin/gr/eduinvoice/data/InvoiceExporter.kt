package gr.eduinvoice.data

import android.net.Uri
import gr.eduinvoice.core.format.Formats
import gr.eduinvoice.core.invoice.InvoiceDocumentBuilder
import gr.eduinvoice.core.model.DomainException
import gr.eduinvoice.core.repository.GroupRepository
import gr.eduinvoice.core.repository.InvoiceRepository
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.SettingsRepository
import gr.eduinvoice.core.repository.StudentRepository
import kotlinx.coroutines.flow.first
import java.util.Locale

/** Builds the PDF for a stored invoice on demand, so PDFs never go stale or need storing. */
class InvoiceExporter(
    private val students: StudentRepository,
    private val groups: GroupRepository,
    private val lessons: LessonRepository,
    private val invoices: InvoiceRepository,
    private val settings: SettingsRepository,
    private val pdfWriter: InvoicePdfWriter,
) {
    suspend fun exportPdf(invoiceId: Long): Uri {
        val invoice = invoices.getInvoice(invoiceId) ?: throw DomainException("Invoice not found")
        val student = students.getStudent(invoice.studentId) ?: throw DomainException("Student not found")
        val appSettings = settings.settings.value
        val document = InvoiceDocumentBuilder.build(
            invoice = invoice,
            student = student,
            lessons = lessons.getLessonsOfInvoice(invoiceId),
            groupNames = groups.observeGroups().first().associate { it.id to it.name },
            settings = appSettings,
            formats = Formats(Locale.getDefault(), appSettings.currencyCode),
        )
        return pdfWriter.write(document)
    }
}
