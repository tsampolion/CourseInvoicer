package gr.edusuite.invoicer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.BusinessProfile
import gr.edusuite.invoicer.core.model.ThemeMode
import gr.edusuite.invoicer.core.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Settings in SharedPreferences (no DataStore dependency), exposed as a StateFlow. */
class PrefsSettingsRepository(context: Context, fileName: String = FILE_NAME) : SettingsRepository {
    private val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.readSettings())
    private val writes = Mutex()

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    /**
     * One update at a time, each written to disk before it is published, and never abandoned halfway:
     * what the screens show is always what survives a restart.
     */
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        writes.withLock {
            withContext(NonCancellable + Dispatchers.IO) {
                val updated = transform(state.value)
                prefs.writeSettings(updated)
                state.value = updated
            }
        }
    }

    private fun SharedPreferences.readSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            business = BusinessProfile(
                name = getString(BUSINESS_NAME, null).orEmpty(),
                address = getString(BUSINESS_ADDRESS, null).orEmpty(),
                taxId = getString(TAX_ID, null).orEmpty(),
                phone = getString(PHONE, null).orEmpty(),
                email = getString(EMAIL, null).orEmpty(),
                iban = getString(IBAN, null).orEmpty(),
            ),
            currencyCode = getString(CURRENCY, null) ?: defaults.currencyCode,
            defaultLessonMinutes = getInt(DEFAULT_MINUTES, defaults.defaultLessonMinutes),
            themeMode = ThemeMode.entries.firstOrNull { it.name == getString(THEME, null) } ?: defaults.themeMode,
            invoiceFooter = getString(FOOTER, null).orEmpty(),
        )
    }

    private fun SharedPreferences.writeSettings(settings: AppSettings) {
        edit(commit = true) {
            putString(BUSINESS_NAME, settings.business.name)
            putString(BUSINESS_ADDRESS, settings.business.address)
            putString(TAX_ID, settings.business.taxId)
            putString(PHONE, settings.business.phone)
            putString(EMAIL, settings.business.email)
            putString(IBAN, settings.business.iban)
            putString(CURRENCY, settings.currencyCode)
            putInt(DEFAULT_MINUTES, settings.defaultLessonMinutes)
            putString(THEME, settings.themeMode.name)
            putString(FOOTER, settings.invoiceFooter)
        }
    }

    companion object {
        const val FILE_NAME = "settings"

        private const val BUSINESS_NAME = "business_name"
        private const val BUSINESS_ADDRESS = "business_address"
        private const val TAX_ID = "tax_id"
        private const val PHONE = "phone"
        private const val EMAIL = "email"
        private const val IBAN = "iban"
        private const val CURRENCY = "currency"
        private const val DEFAULT_MINUTES = "default_minutes"
        private const val THEME = "theme"
        private const val FOOTER = "invoice_footer"
    }
}
