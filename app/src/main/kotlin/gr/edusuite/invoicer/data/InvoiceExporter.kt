package gr.edusuite.invoicer.data

import android.net.Uri
import gr.edusuite.invoicer.core.usecase.GetInvoiceDocument

/** Renders an invoice's issued document (see [GetInvoiceDocument]) to a shareable PDF. */
class InvoiceExporter(
    private val getDocument: GetInvoiceDocument,
    private val pdfWriter: InvoicePdfWriter,
) {
    suspend fun exportPdf(invoiceId: Long): Uri = pdfWriter.write(getDocument(invoiceId))
}
