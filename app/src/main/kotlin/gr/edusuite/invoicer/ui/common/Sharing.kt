package gr.edusuite.invoicer.ui.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Returns a function that renders an invoice to PDF and opens the system share sheet. Taps while a PDF is
 * still being prepared are ignored, so a double tap opens one share sheet.
 */
@Composable
fun rememberInvoiceSharer(onError: (String) -> Unit): (Long) -> Unit {
    val context = LocalContext.current
    val exporter = LocalAppContainer.current.invoiceExporter
    val scope = rememberCoroutineScope()
    val reportError by rememberUpdatedState(onError)
    val exporting = remember { AtomicBoolean(false) }
    return remember(context, exporter, scope) {
        { invoiceId ->
            if (exporting.compareAndSet(false, true)) {
                scope.launch {
                    try {
                        val uri = exporter.exportPdf(invoiceId)
                        context.startActivity(pdfShareIntent(uri))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ActivityNotFoundException) {
                        reportError("No app can share PDF files")
                    } catch (e: Exception) {
                        reportError("Could not create the PDF: ${e.message ?: e.javaClass.simpleName}")
                    } finally {
                        exporting.set(false)
                    }
                }
            }
        }
    }
}

fun pdfShareIntent(uri: Uri): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, "Share invoice")
}
