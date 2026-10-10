package gr.eduinvoice.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import gr.eduinvoice.core.model.AppSettings
import gr.eduinvoice.core.model.BusinessProfile
import gr.eduinvoice.core.model.ThemeMode
import gr.eduinvoice.core.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext

/** Settings in SharedPreferences (no DataStore dependency), exposed as a StateFlow. */
class PrefsSettingsRepository(context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.readSettings())

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = state.updateAndGet(transform)
        withContext(Dispatchers.IO) { prefs.writeSettings(updated) }
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

    private companion object {
        const val BUSINESS_NAME = "business_name"
        const val BUSINESS_ADDRESS = "business_address"
        const val TAX_ID = "tax_id"
        const val PHONE = "phone"
        const val EMAIL = "email"
        const val IBAN = "iban"
        const val CURRENCY = "currency"
        const val DEFAULT_MINUTES = "default_minutes"
        const val THEME = "theme"
        const val FOOTER = "invoice_footer"
    }
}
