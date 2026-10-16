package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.BusinessProfile
import gr.edusuite.invoicer.core.testing.InMemorySettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsUseCasesTest {
    private val repository = InMemorySettingsRepository()
    private val save = SaveSettings(repository)

    @Test
    fun `saves trimmed settings with an upper-case currency`() = runTest {
        val typed = AppSettings(
            business = BusinessProfile(name = " Tutor "),
            currencyCode = " gbp ",
            invoiceFooter = " Thanks ",
        )
        save(typed)

        val saved = repository.settings.value
        assertEquals("Tutor", saved.business.name)
        assertEquals("GBP", saved.currencyCode)
        assertEquals("Thanks", saved.invoiceFooter)
    }

    @Test
    fun `rejects unknown currencies and impossible lesson lengths`() = runTest {
        assertRejected("currency") { save(AppSettings(currencyCode = "XX")) }
        assertRejected("Default lesson length") { save(AppSettings(defaultLessonMinutes = 0)) }
        assertRejected("Default lesson length") { save(AppSettings(defaultLessonMinutes = MAX_LESSON_MINUTES + 1)) }
        assertEquals("nothing is saved", AppSettings(), repository.settings.value)
    }
}
