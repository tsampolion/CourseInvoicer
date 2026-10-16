package gr.edusuite.invoicer.ui.invoices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.ui.common.AppIcons
import gr.edusuite.invoicer.ui.common.ChipTone
import gr.edusuite.invoicer.ui.common.ConfirmDialog
import gr.edusuite.invoicer.ui.common.EmptyState
import gr.edusuite.invoicer.ui.common.LocalFormats
import gr.edusuite.invoicer.ui.common.MessageEffect
import gr.edusuite.invoicer.ui.common.ScreenScaffold
import gr.edusuite.invoicer.ui.common.StatusChip
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.common.rememberInvoiceSharer

@Composable
fun InvoicesScreen(
    onCreateInvoice: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InvoicesViewModel = appViewModel {
        InvoicesViewModel(it.invoiceRepository, it.studentRepository, it.lessonRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    val snackbar = remember { SnackbarHostState() }
    var toVoid by remember { mutableStateOf<Invoice?>(null) }
    val shareInvoice = rememberInvoiceSharer(viewModel::showMessage)
    MessageEffect(state.message, snackbar, viewModel::messageShown)

    ScreenScaffold(
        title = "Statements",
        modifier = modifier,
        snackbarHostState = snackbar,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateInvoice) { Icon(AppIcons.Add, contentDescription = "New statement") }
        },
        bottomBar = bottomBar,
    ) { padding ->
        when {
            state.loading -> Unit
            state.items.isEmpty() -> EmptyState(
                "No statements yet. Tap + to list a student's lessons on one.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.padding(padding)) {
                items(state.items, key = { it.invoice.id }) { item ->
                    ListItem(
                        headlineContent = { Text("${item.invoice.number} · ${item.studentName}") },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formats.date(item.invoice.issueDate), Modifier.padding(end = 8.dp))
                                when {
                                    item.invoice.isVoid -> StatusChip("Void", ChipTone.Neutral)
                                    item.paid -> StatusChip("Paid", ChipTone.Good)
                                    else -> StatusChip("Unpaid", ChipTone.Warning)
                                }
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formats.money(item.invoice.total), style = MaterialTheme.typography.titleSmall)
                                }
                                if (!item.invoice.isVoid) {
                                    IconButton(onClick = { toVoid = item.invoice }) {
                                        Icon(
                                            AppIcons.Close,
                                            contentDescription = "Void statement ${item.invoice.number}",
                                        )
                                    }
                                }
                            }
                        },
                        leadingContent = {
                            if (!item.invoice.isVoid) Icon(AppIcons.Share, contentDescription = "Share")
                        },
                        modifier = Modifier.clickable(enabled = !item.invoice.isVoid) { shareInvoice(item.invoice.id) },
                    )
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    toVoid?.let { invoice ->
        ConfirmDialog(
            title = "Void statement ${invoice.number}",
            text = "The statement stays in the list as void and its number is never reused. Its lessons can " +
                "go on a new statement. Payment records are kept.",
            confirmLabel = "Void",
            onConfirm = { viewModel.markVoid(invoice) },
            onDismiss = { toVoid = null },
        )
    }
}
