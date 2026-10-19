package gr.edusuite.invoicer.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
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
 * Returns a function that renders a statement (an invoice in code) to PDF and opens the system share sheet.
 * Taps while a PDF is still being prepared are ignored, so a double tap opens one share sheet.
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
    return Intent.createChooser(send, "Share statement")
}

/**
 * Opens the system share sheet with plain [text] (for example the privacy notice), [title] as its subject
 * and the chooser's title. Returns false when no app can receive text.
 */
fun shareText(context: Context, title: String, text: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return try {
        context.startActivity(Intent.createChooser(send, title))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/**
 * Returns a function that writes one student's data export (JSON) and opens the share sheet for it.
 * [json] is built by the caller (StudentDetailViewModel.exportData); taps while one is being written are
 * ignored.
 */
@Composable
fun rememberStudentDataSharer(onError: (String) -> Unit): (fileStem: String, json: String) -> Unit {
    val context = LocalContext.current
    val files = LocalAppContainer.current.studentDataFiles
    val scope = rememberCoroutineScope()
    val reportError by rememberUpdatedState(onError)
    val exporting = remember { AtomicBoolean(false) }
    return remember(context, files, scope) {
        { fileStem, json ->
            if (exporting.compareAndSet(false, true)) {
                scope.launch {
                    try {
                        val uri = files.write(fileStem, json)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share student data"))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ActivityNotFoundException) {
                        reportError("No app can share this file")
                    } catch (e: Exception) {
                        reportError("Could not export the data: ${e.message ?: e.javaClass.simpleName}")
                    } finally {
                        exporting.set(false)
                    }
                }
            }
        }
    }
}
