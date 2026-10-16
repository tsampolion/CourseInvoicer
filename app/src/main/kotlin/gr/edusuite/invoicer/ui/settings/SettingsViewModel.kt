package gr.edusuite.invoicer.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import gr.edusuite.invoicer.core.backup.BackupFormatException
import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.ReminderSettings
import gr.edusuite.invoicer.core.model.ThemeMode
import gr.edusuite.invoicer.core.repository.SettingsRepository
import gr.edusuite.invoicer.core.usecase.SaveSettings
import gr.edusuite.invoicer.core.usecase.SettingsRules
import gr.edusuite.invoicer.data.BackupFiles
import gr.edusuite.invoicer.ui.common.launchCatching
import gr.edusuite.invoicer.ui.common.plural
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** How long typing pauses before the settings are saved. */
const val SAVE_DEBOUNCE_MS = 400L

/** The text in the Settings fields, as typed. What is stored is `SettingsRepository.settings`. */
data class SettingsForm(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val iban: String = "",
    val currencyCode: String = "EUR",
    val defaultMinutes: String = "60",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val footer: String = "",
    val reminders: ReminderSettings = ReminderSettings(),
)

data class SettingsUiState(
    val form: SettingsForm = SettingsForm(),
    val currencyError: Boolean = false,
    val minutesError: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * Settings save as the user types (R13): text fields [SAVE_DEBOUNCE_MS] after the last keystroke,
 * the theme at once. A currency or lesson length that breaks the rules is never stored: the field
 * shows an error and the stored value stays, while the other fields still save.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backupFiles: BackupFiles,
) : ViewModel() {
    private val saveSettings = SaveSettings(settings)
    private val mutableState = MutableStateFlow(SettingsUiState(form = settings.settings.value.toForm()))
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    /** The debounced save, while one is waiting or running. */
    private var pendingSave: Job? = null

    fun onName(text: String) = edit { it.copy(name = text) }

    fun onAddress(text: String) = edit { it.copy(address = text) }

    fun onPhone(text: String) = edit { it.copy(phone = text) }

    fun onEmail(text: String) = edit { it.copy(email = text) }

    fun onIban(text: String) = edit { it.copy(iban = text) }

    fun onFooter(text: String) = edit { it.copy(footer = text) }

    fun onCurrency(text: String) {
        // The error appears when the value would be saved, and clears as soon as the text is valid.
        if (SettingsRules.normalisedCurrency(text) != null) mutableState.update { it.copy(currencyError = false) }
        edit { it.copy(currencyCode = text) }
    }

    fun onMinutes(text: String) {
        if (SettingsRules.lessonMinutes(text) != null) mutableState.update { it.copy(minutesError = false) }
        edit { it.copy(defaultMinutes = text) }
    }

    /** The theme applies at once, so it is saved without waiting (with any typing still pending). */
    fun onTheme(mode: ThemeMode) {
        mutableState.update { it.copy(form = it.form.copy(themeMode = mode)) }
        pendingSave?.cancel()
        pendingSave = null
        launchCatching(::showMessage) { commit() }
    }

    fun onRemindersEnabled(on: Boolean) = setReminders { it.copy(enabled = on) }

    fun onRemindEarlyDecember(on: Boolean) = setReminders { it.copy(earlyDecember = on) }

    fun onRemindEarlyJune(on: Boolean) = setReminders { it.copy(earlyJune = on) }

    fun onRemindEarlyEaster(on: Boolean) = setReminders { it.copy(earlyEaster = on) }

    /** Like the theme, a reminder switch is saved at once (with any typing still pending). */
    private fun setReminders(transform: (ReminderSettings) -> ReminderSettings) {
        mutableState.update { it.copy(form = it.form.copy(reminders = transform(it.form.reminders))) }
        pendingSave?.cancel()
        pendingSave = null
        launchCatching(::showMessage) { commit() }
    }

    /** Saves a change that is still waiting for typing to pause. Call it when the screen is left. */
    fun flush() {
        if (pendingSave?.isActive != true) return
        launchCatching(::showMessage) { savePendingNow() }
    }

    fun exportBackup(uri: Uri) = runBusy {
        savePendingNow()
        backupFiles.export(uri)
        showMessage("Backup saved")
    }

    fun importBackup(uri: Uri) = runBusy {
        // The backup replaces the settings, so typing that was not saved yet is dropped, not saved over it.
        pendingSave?.cancel()
        pendingSave = null
        try {
            val snapshot = backupFiles.import(uri)
            mutableState.update {
                it.copy(form = settings.settings.value.toForm(), currencyError = false, minutesError = false)
            }
            showMessage(
                "Restored ${plural(snapshot.students.size, "student")}, ${plural(snapshot.lessons.size, "lesson")} " +
                    "and ${plural(snapshot.invoices.size, "invoice")}",
            )
        } catch (e: BackupFormatException) {
            showMessage("Not a valid backup: ${e.message}")
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun edit(transform: (SettingsForm) -> SettingsForm) {
        mutableState.update { it.copy(form = transform(it.form)) }
        pendingSave?.cancel()
        pendingSave = launchCatching(::showMessage) {
            delay(SAVE_DEBOUNCE_MS)
            commit()
        }
    }

    private suspend fun savePendingNow() {
        val pending = pendingSave ?: return
        pendingSave = null
        if (!pending.isActive) return
        pending.cancel()
        commit()
    }

    /**
     * Saves the form over the stored settings. A field that breaks the rules keeps its stored value.
     * Once started, the write finishes even if the screen is closed or another keystroke arrives.
     */
    private suspend fun commit() = withContext(NonCancellable) {
        val form = state.value.form
        val currency = SettingsRules.normalisedCurrency(form.currencyCode)
        val minutes = SettingsRules.lessonMinutes(form.defaultMinutes)
        mutableState.update { it.copy(currencyError = currency == null, minutesError = minutes == null) }
        val stored = settings.settings.value
        saveSettings(
            stored.copy(
                business = stored.business.copy(
                    name = form.name,
                    address = form.address,
                    phone = form.phone,
                    email = form.email,
                    iban = form.iban,
                ),
                currencyCode = currency ?: stored.currencyCode,
                defaultLessonMinutes = minutes ?: stored.defaultLessonMinutes,
                themeMode = form.themeMode,
                invoiceFooter = form.footer,
                reminders = form.reminders,
            ),
        )
    }

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
        phone = business.phone,
        email = business.email,
        iban = business.iban,
        currencyCode = currencyCode,
        defaultMinutes = defaultLessonMinutes.toString(),
        themeMode = themeMode,
        footer = invoiceFooter,
        reminders = reminders,
    )
}
