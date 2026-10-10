package gr.eduinvoice.ui.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.eduinvoice.ui.common.AppIcons
import gr.eduinvoice.ui.common.ConfirmDialog
import gr.eduinvoice.ui.common.DateField
import gr.eduinvoice.ui.common.DecimalField
import gr.eduinvoice.ui.common.FormSpacing
import gr.eduinvoice.ui.common.LocalFormats
import gr.eduinvoice.ui.common.MessageEffect
import gr.eduinvoice.ui.common.ScreenScaffold
import gr.eduinvoice.ui.common.StudentPickerField
import gr.eduinvoice.ui.common.TimeField
import gr.eduinvoice.ui.common.appViewModel

@Composable
fun LessonEditScreen(
    lessonId: Long,
    studentId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LessonEditViewModel = appViewModel {
        LessonEditViewModel(
            lessonId = lessonId,
            initialStudentId = studentId,
            students = it.studentRepository,
            groups = it.groupRepository,
            lessons = it.lessonRepository,
            settings = it.settingsRepository,
        )
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    LaunchedEffect(state.done) { if (state.done) onDone() }
    val editable = !state.locked

    ScreenScaffold(
        title = if (state.isNew) "Log lesson" else "Edit lesson",
        modifier = modifier,
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (!state.isNew && editable) {
                IconButton(onClick = { confirmDelete = true }) { Icon(AppIcons.Delete, contentDescription = "Delete") }
            }
            TextButton(onClick = viewModel::save, enabled = !state.loading && !state.saving) { Text("Save") }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = FormSpacing,
        ) {
            if (state.locked) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "This lesson is on an invoice. Delete the invoice to change its date, length or fee.",
                        Modifier.padding(12.dp),
                    )
                }
            }
            StudentPickerField(
                students = state.students,
                selectedId = state.studentId,
                onSelect = viewModel::selectStudent,
                enabled = editable && state.groupName == null,
                isError = state.studentError,
            )
            state.groupName?.let { Text("Group lesson · $it", style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Date", state.date, viewModel::setDate, Modifier.weight(1f), enabled = editable)
                TimeField("Start", state.time, viewModel::setTime, Modifier.weight(1f), enabled = editable)
            }
            DurationInput(state.duration, viewModel::setDuration, enabled = editable, isError = state.durationError)
            DecimalField(
                label = "Fee *",
                value = state.fee,
                onValueChange = viewModel::setFee,
                prefix = LocalFormats.current.currencySymbol,
                enabled = editable,
                isError = state.feeError,
                supportingText = if (state.feeError) "Enter an amount" else "Calculated from the rate; you can change it",
            )
            SwitchRow("Absent (not charged)", state.absent, viewModel::setAbsent, enabled = editable)
            SwitchRow("Paid", state.paid, viewModel::setPaid, enabled = !state.absent)
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Notes (e.g. topic, homework)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete lesson",
            text = "This lesson will be removed permanently.",
            confirmLabel = "Delete",
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
