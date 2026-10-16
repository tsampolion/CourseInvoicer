package gr.eduinvoice.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import gr.eduinvoice.core.backup.BackupFormatException
import gr.eduinvoice.core.model.AppSettings
import gr.eduinvoice.core.model.BusinessProfile
import gr.eduinvoice.core.model.ThemeMode
import gr.eduinvoice.core.repository.SettingsRepository
import gr.eduinvoice.core.usecase.SaveSettings
import gr.eduinvoice.core.usecase.SettingsRules
import gr.eduinvoice.data.BackupFiles
import gr.eduinvoice.ui.common.launchCatching
import gr.eduinvoice.ui.common.plural
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class SettingsForm(
    val name: String = "",
    val address: String = "",
    val taxId: String = "",
    val phone: String = "",
    val email: String = "",
    val iban: String = "",
    val currencyCode: String = "EUR",
    val defaultMinutes: String = "60",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val footer: String = "",
)

data class SettingsUiState(
    val form: SettingsForm = SettingsForm(),
    val currencyError: Boolean = false,
    val minutesError: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backupFiles: BackupFiles,
) : ViewModel() {
    private val saveSettings = SaveSettings(settings)
    private val mutableState = MutableStateFlow(SettingsUiState(form = settings.settings.value.toForm()))
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    fun update(transform: (SettingsForm) -> SettingsForm) = mutableState.update {
        it.copy(form = transform(it.form), currencyError = false, minutesError = false)
    }

    fun save() {
        val form = state.value.form
        // Highlight the fields first; SaveSettings applies the same rules (and trims) when saving.
        val currency = form.currencyCode.trim().uppercase(Locale.ROOT)
        val currencyValid = SettingsRules.isValidCurrency(currency)
        val minutes = form.defaultMinutes.toIntOrNull()?.takeIf(SettingsRules::isValidLessonMinutes)
        if (!currencyValid || minutes == null) {
            mutableState.update { it.copy(currencyError = !currencyValid, minutesError = minutes == null) }
            return
        }
        launchCatching(::showMessage) {
            saveSettings(
                AppSettings(
                    business = BusinessProfile(
                        name = form.name,
                        address = form.address,
                        taxId = form.taxId,
                        phone = form.phone,
                        email = form.email,
                        iban = form.iban,
                    ),
                    currencyCode = currency,
                    defaultLessonMinutes = minutes,
                    themeMode = form.themeMode,
                    invoiceFooter = form.footer,
                ),
            )
            mutableState.update { it.copy(form = settings.settings.value.toForm()) }
            showMessage("Settings saved")
        }
    }

    fun exportBackup(uri: Uri) = runBusy {
        backupFiles.export(uri)
        showMessage("Backup saved")
    }

    fun importBackup(uri: Uri) = runBusy {
        try {
            val snapshot = backupFiles.import(uri)
            mutableState.update { it.copy(form = settings.settings.value.toForm()) }
            showMessage(
                "Restored ${plural(snapshot.students.size, "student")}, ${plural(snapshot.lessons.size, "lesson")} " +
                    "and ${plural(snapshot.invoices.size, "invoice")}",
            )
        } catch (e: BackupFormatException) {
            showMessage("Not a valid backup: ${e.message}")
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun runBusy(block: suspend () -> Unit) {
        mutableState.update { it.copy(busy = true) }
        launchCatching(::showMessage) {
            try {
                block()
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private fun showMessage(message: String) = mutableState.update { it.copy(message = message) }

    private fun AppSettings.toForm() = SettingsForm(
        name = business.name,
        address = business.address,
        taxId = business.taxId,
        phone = business.phone,
        email = business.email,
        iban = business.iban,
        currencyCode = currencyCode,
        defaultMinutes = defaultLessonMinutes.toString(),
        themeMode = themeMode,
        footer = invoiceFooter,
    )
}
