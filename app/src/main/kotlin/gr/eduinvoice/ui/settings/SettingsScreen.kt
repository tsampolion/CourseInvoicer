package gr.eduinvoice.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.eduinvoice.BuildConfig
import gr.eduinvoice.core.model.ThemeMode
import gr.eduinvoice.ui.common.ConfirmDialog
import gr.eduinvoice.ui.common.FormSpacing
import gr.eduinvoice.ui.common.MessageEffect
import gr.eduinvoice.ui.common.ScreenScaffold
import gr.eduinvoice.ui.common.appViewModel
import gr.eduinvoice.ui.students.TextInput
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

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        pendingImport = it
    }

    ScreenScaffold(
        title = "Settings",
        modifier = modifier,
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = { TextButton(onClick = viewModel::save, enabled = !state.busy) { Text("Save") } },
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

            Section("Your details", "Printed at the top of every invoice.")
            TextInput("Name or business name", form.name, { v -> viewModel.update { it.copy(name = v) } }, capitalize = true)
            OutlinedTextField(
                value = form.address,
                onValueChange = { v -> viewModel.update { it.copy(address = v) } },
                label = { Text("Address") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            TextInput("Tax ID (ΑΦΜ)", form.taxId, { v -> viewModel.update { it.copy(taxId = v) } })
            TextInput("Phone", form.phone, { v -> viewModel.update { it.copy(phone = v) } }, keyboardType = KeyboardType.Phone)
            TextInput("Email", form.email, { v -> viewModel.update { it.copy(email = v) } }, keyboardType = KeyboardType.Email)
            TextInput("IBAN for payments", form.iban, { v -> viewModel.update { it.copy(iban = v) } })

            Section("Invoices & lessons")
            TextInput(
                "Currency code (e.g. EUR)",
                form.currencyCode,
                { v -> viewModel.update { it.copy(currencyCode = v.take(3)) } },
                isError = state.currencyError,
                error = "Use a 3-letter ISO code such as EUR, GBP or USD",
            )
            TextInput(
                "Default lesson length (minutes)",
                form.defaultMinutes,
                { v -> viewModel.update { it.copy(defaultMinutes = v.filter(Char::isDigit).take(3)) } },
                isError = state.minutesError,
                error = "Between 1 and 720",
                keyboardType = KeyboardType.Number,
            )
            OutlinedTextField(
                value = form.footer,
                onValueChange = { v -> viewModel.update { it.copy(footer = v) } },
                label = { Text("Invoice footer (e.g. thank-you note)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Section("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = form.themeMode == mode,
                        onClick = { viewModel.update { it.copy(themeMode = mode) } },
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

            Section("Backup", "Everything stays on this device. Export a backup regularly and keep it somewhere safe.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportLauncher.launch("eduinvoice-backup-${LocalDate.now()}.json") },
                    enabled = !state.busy,
                ) { Text("Export") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    enabled = !state.busy,
                ) { Text("Import") }
            }

            HorizontalDivider()
            Text(
                "EduInvoice ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Replace all data?",
            text = "Importing replaces every student, lesson, invoice and setting on this device with the backup.",
            confirmLabel = "Replace",
            onConfirm = { viewModel.importBackup(uri) },
            onDismiss = { pendingImport = null },
        )
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
