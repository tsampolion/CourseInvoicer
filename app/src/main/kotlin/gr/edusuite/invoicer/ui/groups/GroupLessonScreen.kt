package gr.edusuite.invoicer.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.ui.common.DateField
import gr.edusuite.invoicer.ui.common.FormSpacing
import gr.edusuite.invoicer.ui.common.LocalFormats
import gr.edusuite.invoicer.ui.common.MessageEffect
import gr.edusuite.invoicer.ui.common.ScreenScaffold
import gr.edusuite.invoicer.ui.common.TimeField
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.lessons.DurationInput

@Composable
fun GroupLessonScreen(
    groupId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupLessonViewModel = appViewModel {
        GroupLessonViewModel(groupId, it.groupRepository, it.studentRepository, it.lessonRepository, it.settingsRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    LaunchedEffect(state.done) { if (state.done) onDone() }

    ScreenScaffold(
        title = state.group?.name ?: "Group lesson",
        modifier = modifier,
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            TextButton(
                onClick = viewModel::save,
                enabled = !state.loading && !state.saving && state.attendees.isNotEmpty(),
            ) { Text("Log") }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Date", state.date, viewModel::setDate, Modifier.weight(1f))
                TimeField("Start", state.time, viewModel::setTime, Modifier.weight(1f))
            }
            DurationInput(state.duration, viewModel::setDuration, isError = state.durationError)

            Text("Attendance", style = MaterialTheme.typography.titleSmall)
            if (!state.loading && state.attendees.isEmpty()) {
                Text("This group has no active members. Edit the group to add students.")
            }
            state.attendees.forEach { attendee ->
                AttendeeRow(
                    attendee = attendee,
                    onPresence = { viewModel.setPresence(attendee.student.id, it) },
                    onReason = { viewModel.setReason(attendee.student.id, it) },
                )
            }

            state.feePerStudent?.let { fee ->
                Text(
                    "${state.presentCount} present · ${state.chargedCount} charged × ${formats.money(fee)} = " +
                        formats.money(fee * state.chargedCount),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                "Absent students are recorded but not charged. Late cancellations (under 24 hours) are charged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Notes (e.g. topic)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One member: Present, Absent or Late cancel, and a reason when absent. */
@Composable
private fun AttendeeRow(
    attendee: Attendee,
    onPresence: (Presence) -> Unit,
    onReason: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(attendee.student.fullName, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Presence.entries.forEach { presence ->
                FilterChip(
                    selected = attendee.presence == presence,
                    onClick = { onPresence(presence) },
                    label = {
                        Text(
                            when (presence) {
                                Presence.PRESENT -> "Present"
                                Presence.ABSENT -> "Absent"
                                Presence.LATE_CANCEL -> "Late cancel"
                            },
                        )
                    },
                )
            }
        }
        if (!attendee.present) {
            OutlinedTextField(
                value = attendee.reason,
                onValueChange = onReason,
                label = { Text("Reason (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
