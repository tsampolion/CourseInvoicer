package gr.edusuite.invoicer.ui.invoices

import androidx.lifecycle.ViewModel
import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.repository.SettingsRepository
import gr.edusuite.invoicer.core.usecase.SaveSettings
import gr.edusuite.invoicer.ui.common.launchCatching
import gr.edusuite.invoicer.ui.settings.SAVE_DEBOUNCE_MS
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** The text in the Statement details fields, as typed. What is stored is `SettingsRepository.settings`. */
data class StatementDetailsForm(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val iban: String = "",
    val footer: String = "",
    val showAddressOnStatements: Boolean = false,
)

data class StatementDetailsUiState(
    val form: StatementDetailsForm = StatementDetailsForm(),
    val message: String? = null,
)

/**
 * The tutor's details printed on every statement (R13, R14). They save as the user types, like Settings:
 * text fields [SAVE_DEBOUNCE_MS] after the last keystroke, the show-address switch at once.
 */
class StatementDetailsViewModel(private val settings: SettingsRepository) : ViewModel() {
    private val saveSettings = SaveSettings(settings)
    private val mutableState = MutableStateFlow(StatementDetailsUiState(form = settings.settings.value.toForm()))
    val state: StateFlow<StatementDetailsUiState> = mutableState.asStateFlow()

    /** The debounced save, while one is waiting or running. */
    private var pendingSave: Job? = null

    fun onName(text: String) = edit { it.copy(name = text) }

    fun onAddress(text: String) = edit { it.copy(address = text) }

    fun onPhone(text: String) = edit { it.copy(phone = text) }

    fun onEmail(text: String) = edit { it.copy(email = text) }

    fun onIban(text: String) = edit { it.copy(iban = text) }

    fun onFooter(text: String) = edit { it.copy(footer = text) }

    /** A switch saves at once, with any typing still pending. */
    fun onShowAddressOnStatements(show: Boolean) {
        mutableState.update { it.copy(form = it.form.copy(showAddressOnStatements = show)) }
        pendingSave?.cancel()
        pendingSave = null
        launchCatching(::showMessage) { commit() }
    }

    /** Saves a change that is still waiting for typing to pause. Call it when the screen is left. */
    fun flush() {
        val pending = pendingSave ?: return
        pendingSave = null
        if (!pending.isActive) return
        pending.cancel()
        launchCatching(::showMessage) { commit() }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun edit(transform: (StatementDetailsForm) -> StatementDetailsForm) {
        mutableState.update { it.copy(form = transform(it.form)) }
        pendingSave?.cancel()
        pendingSave = launchCatching(::showMessage) {
            delay(SAVE_DEBOUNCE_MS)
            commit()
        }
    }

    /** Saves these fields over the stored settings. Once started, the write finishes even if the screen closes. */
    private suspend fun commit() = withContext(NonCancellable) {
        val form = state.value.form
        settings.settings.value.let { stored ->
            saveSettings(
                stored.copy(
                    business = stored.business.copy(
                        name = form.name,
                        address = form.address,
                        phone = form.phone,
                        email = form.email,
                        iban = form.iban,
                    ),
                    invoiceFooter = form.footer,
                    showAddressOnStatements = form.showAddressOnStatements,
                ),
            )
        }
    }

    private fun showMessage(message: String) = mutableState.update { it.copy(message = message) }

    private fun AppSettings.toForm() = StatementDetailsForm(
        name = business.name,
        address = business.address,
        phone = business.phone,
        email = business.email,
        iban = business.iban,
        footer = invoiceFooter,
        showAddressOnStatements = showAddressOnStatements,
    )
}
