package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.repository.SettingsRepository

/** What makes settings valid. Used when saving them and when restoring a backup (R13; R11 on restore). */
object SettingsRules {
    fun isValidLessonMinutes(minutes: Int): Boolean = isValidDuration(minutes)

    /** [text] as a default lesson length in minutes, or null if it is not a whole number in range. */
    fun lessonMinutes(text: String): Int? = text.trim().toIntOrNull()?.takeIf { isValidLessonMinutes(it) }

    fun validate(settings: AppSettings) {
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
                phone = new.business.phone.trim(),
                email = new.business.email.trim(),
                iban = new.business.iban.trim(),
            ),
            invoiceFooter = new.invoiceFooter.trim(),
        )
        SettingsRules.validate(normalized)
        settings.update { normalized }
    }
}
