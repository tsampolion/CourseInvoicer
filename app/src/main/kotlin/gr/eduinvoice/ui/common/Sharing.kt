package gr.eduinvoice.ui.common

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

/** Returns a function that renders an invoice to PDF and opens the system share sheet. */
@Composable
fun rememberInvoiceSharer(onError: (String) -> Unit): (Long) -> Unit {
    val context = LocalContext.current
    val exporter = LocalAppContainer.current.invoiceExporter
    val scope = rememberCoroutineScope()
    val reportError by rememberUpdatedState(onError)
    return remember(context, exporter, scope) {
        { invoiceId ->
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
