package gr.edusuite.invoicer.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.ui.common.AppIcons
import gr.edusuite.invoicer.ui.common.ConfirmDialog
import gr.edusuite.invoicer.ui.common.FormSpacing
import gr.edusuite.invoicer.ui.common.MessageEffect
import gr.edusuite.invoicer.ui.common.ScreenScaffold
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.students.RateInput
import gr.edusuite.invoicer.ui.students.TextInput

@Composable
fun GroupEditScreen(
    groupId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupEditViewModel = appViewModel {
        GroupEditViewModel(groupId, it.groupRepository, it.studentRepository, it.lessonRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val form = state.form
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    LaunchedEffect(state.done) { if (state.done) onDone() }

    ScreenScaffold(
        title = if (state.isNew) "New group" else "Edit group",
        modifier = modifier,
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (!state.isNew) {
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
            TextInput("Group name *", form.name, { v -> viewModel.update { it.copy(name = v) } },
                isError = state.nameError, error = "Required", capitalize = true)
            TextInput("Class / level", form.className, { v -> viewModel.update { it.copy(className = v) } })
            RateInput(
                amount = form.rate,
                type = form.rateType,
                onAmountChange = { v -> viewModel.update { it.copy(rate = v) } },
                onTypeChange = { v -> viewModel.update { it.copy(rateType = v) } },
                isError = state.rateError,
            )
            Text(
                "Each attending student is charged this rate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.isNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Archived", Modifier.weight(1f))
                    Switch(checked = form.archived, onCheckedChange = { v -> viewModel.update { it.copy(archived = v) } })
                }
            }
            Text("Members (${form.memberIds.size})", style = MaterialTheme.typography.titleSmall)
            if (state.candidates.isEmpty() && !state.loading) {
                Text("Add students first, then come back to pick the members.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                state.candidates.forEach { student ->
                    val checked = student.id in form.memberIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = checked, role = Role.Checkbox, onValueChange = {
                                viewModel.toggleMember(student.id)
                            })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            student.fullName + if (student.archived) " (archived)" else "",
                            Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete group",
            text = "The group is removed. Lessons already logged for it are kept as individual lessons.",
            confirmLabel = "Delete",
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}
