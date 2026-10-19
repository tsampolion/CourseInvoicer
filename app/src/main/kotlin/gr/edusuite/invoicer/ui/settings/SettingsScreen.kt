package gr.edusuite.invoicer.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import gr.edusuite.invoicer.ui.common.SuiteSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.BuildConfig
import gr.edusuite.invoicer.core.model.ThemeMode
import gr.edusuite.invoicer.core.usecase.MAX_LESSON_MINUTES
import gr.edusuite.invoicer.ui.common.CommitField
import gr.edusuite.invoicer.ui.common.ConfirmDialog
import gr.edusuite.invoicer.ui.common.FormSpacing
import gr.edusuite.invoicer.ui.common.MessageEffect
import gr.edusuite.invoicer.ui.common.ScreenScaffold
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.lessons.SwitchRow
import java.time.LocalDate

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = appViewModel { SettingsViewModel(it.settingsRepository, it.backupFiles) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val form = state.form
    MessageEffect(state.message, snackbar, viewModel::messageShown)

    // Settings save as you type; save what is still waiting before the screen goes away. System Back
    // clears this screen's ViewModel at once, so it is handled here rather than by the NavHost.
    val leave = {
        viewModel.flush()
        onBack()
    }
    BackHandler(onBack = leave)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        pendingImport = it
    }

    ScreenScaffold(
        title = "Settings",
        modifier = modifier,
        onBack = leave,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = FormSpacing,
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Section("Your details", "Printed at the top of every statement.")
            CommitField(
                value = form.name,
                onValueChange = viewModel::onName,
                label = "Name or business name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            CommitField(
                value = form.address,
                onValueChange = viewModel::onAddress,
                label = "Address",
                singleLine = false,
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            SwitchRow(
                "Show address on statements",
                form.showAddressOnStatements,
                viewModel::onShowAddressOnStatements,
            )
            CommitField(
                value = form.phone,
                onValueChange = viewModel::onPhone,
                label = "Phone",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            CommitField(
                value = form.email,
                onValueChange = viewModel::onEmail,
                label = "Email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            CommitField(
                value = form.iban,
                onValueChange = viewModel::onIban,
                label = "IBAN for payments",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )

            Section("Statements & lessons")
            CommitField(
                value = form.defaultMinutes,
                onValueChange = viewModel::onMinutes,
                label = "Default lesson length (minutes)",
                isError = state.minutesError,
                supportingText = if (state.minutesError) "Between 1 and $MAX_LESSON_MINUTES" else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                inputFilter = { text -> text.filter(Char::isDigit).take(3) },
            )
            CommitField(
                value = form.footer,
                onValueChange = viewModel::onFooter,
                label = "Statement footer (e.g. thank-you note)",
                singleLine = false,
                minLines = 2,
            )

            Section("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = form.themeMode == mode,
                        onClick = { viewModel.onTheme(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                },
                            )
                        },
                    )
                }
            }

            Section("Reminders", "From the 29th, Home reminds you of the month's lessons not on a statement yet.")
            SettingSwitch(
                label = "Remind me to send statements at month end",
                checked = form.reminders.enabled,
                onCheckedChange = viewModel::onRemindersEnabled,
            )
            SettingSwitch(
                label = "December: from the 21st",
                checked = form.reminders.earlyDecember,
                onCheckedChange = viewModel::onRemindEarlyDecember,
                enabled = form.reminders.enabled,
            )
            SettingSwitch(
                label = "June: from the 21st",
                checked = form.reminders.earlyJune,
                onCheckedChange = viewModel::onRemindEarlyJune,
                enabled = form.reminders.enabled,
            )
            SettingSwitch(
                label = "Easter month: from the 21st",
                checked = form.reminders.earlyEaster,
                onCheckedChange = viewModel::onRemindEarlyEaster,
                enabled = form.reminders.enabled,
            )

            Section("Backup", "Everything stays on this device. Export a backup regularly and keep it somewhere safe.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportLauncher.launch("course-invoicer-backup-${LocalDate.now()}.json") },
                    enabled = !state.busy,
                ) { Text("Export") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    enabled = !state.busy,
                ) { Text("Import") }
            }

            HorizontalDivider()
            Text(
                "Course Invoicer ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Part of the EduSuite apps, alongside Course Pacer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Replace all data?",
            text = "Importing replaces every student, lesson, statement and setting on this device with the backup.",
            confirmLabel = "Replace",
            onConfirm = { viewModel.importBackup(uri) },
            onDismiss = { pendingImport = null },
        )
    }
}

/** Material's opacity for disabled content. */
private const val DISABLED_ALPHA = 0.38f

/** A switch whose whole row toggles it, so the label is a large touch target and reads as one control. */
@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
        )
        SuiteSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun Section(title: String, description: String? = null) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
