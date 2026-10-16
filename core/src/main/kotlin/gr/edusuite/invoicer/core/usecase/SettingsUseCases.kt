package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.repository.SettingsRepository
import java.util.Currency
import java.util.Locale

/** What makes settings valid. Used when saving them and when restoring a backup (R13; R11 on restore). */
object SettingsRules {
    fun isValidCurrency(code: String): Boolean = runCatching { Currency.getInstance(code) }.isSuccess

    fun isValidLessonMinutes(minutes: Int): Boolean = isValidDuration(minutes)

    fun validate(settings: AppSettings) {
        if (!isValidCurrency(settings.currencyCode)) {
            throw DomainException("'${settings.currencyCode}' is not a currency code, e.g. EUR")
        }
        if (!isValidLessonMinutes(settings.defaultLessonMinutes)) {
            throw DomainException("Default lesson length must be between 1 and $MAX_LESSON_MINUTES minutes")
        }
    }
}

class SaveSettings(private val settings: SettingsRepository) {
    suspend operator fun invoke(new: AppSettings) {
        val normalized = new.copy(
            business = new.business.copy(
                name = new.business.name.trim(),
                address = new.business.address.trim(),
                taxId = new.business.taxId.trim(),
                phone = new.business.phone.trim(),
                email = new.business.email.trim(),
                iban = new.business.iban.trim(),
            ),
            currencyCode = new.currencyCode.trim().uppercase(Locale.ROOT),
            invoiceFooter = new.invoiceFooter.trim(),
        )
        SettingsRules.validate(normalized)
        settings.update { normalized }
    }
}
