package gr.eduinvoice.ui.invoices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.eduinvoice.ui.common.DateField
import gr.eduinvoice.ui.common.LocalFormats
import gr.eduinvoice.ui.common.MessageEffect
import gr.eduinvoice.ui.common.ScreenScaffold
import gr.eduinvoice.ui.common.StudentPickerField
import gr.eduinvoice.ui.common.appViewModel
import gr.eduinvoice.ui.common.plural
import gr.eduinvoice.ui.common.rememberInvoiceSharer
import gr.eduinvoice.ui.lessons.SwitchRow

@Composable
fun CreateInvoiceScreen(
    studentId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateInvoiceViewModel = appViewModel {
        CreateInvoiceViewModel(studentId, it.studentRepository, it.groupRepository, it.lessonRepository, it.invoiceRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    val snackbar = remember { SnackbarHostState() }
    val shareInvoice = rememberInvoiceSharer(viewModel::showMessage)
    MessageEffect(state.message, snackbar, viewModel::messageShown)

    ScreenScaffold(
        title = "New invoice",
        modifier = modifier,
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            TextButton(
                onClick = viewModel::create,
                enabled = !state.saving && state.created == null && state.selected.isNotEmpty(),
            ) { Text("Create") }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    StudentPickerField(state.students, state.studentId, viewModel::selectStudent)
                    DateField("Issue date", state.issueDate, viewModel::setIssueDate, Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::setNotes,
                        label = { Text("Notes on the invoice") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    SwitchRow("Mark these lessons as paid", state.markPaid, viewModel::setMarkPaid)
                    Text(
                        "${plural(state.selected.size, "lesson")} · Total ${formats.money(state.total)}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (state.studentId != null && state.lessons.isEmpty()) {
                item {
                    Text(
                        "This student has no lessons waiting to be invoiced.",
                        Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.lessons.isNotEmpty()) {
                item {
                    val allSelected = state.selected.size == state.lessons.size
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(allSelected, role = Role.Checkbox) { viewModel.selectAll(!allSelected) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = allSelected, onCheckedChange = null)
                        Text("Select all", Modifier.padding(start = 8.dp))
                    }
                    HorizontalDivider()
                }
            }
            items(state.lessons, key = { it.id }) { lesson ->
                val checked = lesson.id in state.selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(checked, role = Role.Checkbox) { viewModel.toggle(lesson.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("${formats.date(lesson.date)} · ${formats.duration(lesson.durationMinutes)}")
                        val group = lesson.groupId?.let { state.groupNames[it] }
                        val detail = listOfNotNull(group, lesson.notes.takeIf { it.isNotBlank() }).joinToString(" · ")
                        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formats.money(lesson.charge))
                }
            }
        }
    }

    state.created?.let { invoice ->
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Invoice ${invoice.number} created") },
            text = { Text("Total ${formats.money(invoice.total)}. Share the PDF with the student now?") },
            confirmButton = { TextButton(onClick = { shareInvoice(invoice.id) }) { Text("Share PDF") } },
            dismissButton = { TextButton(onClick = onDone) { Text("Done") } },
        )
    }
}
