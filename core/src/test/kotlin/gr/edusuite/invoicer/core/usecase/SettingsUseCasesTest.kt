package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.BusinessProfile
import gr.edusuite.invoicer.core.testing.InMemorySettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `reads a typed currency code as the code to store, or nothing`() {
        assertEquals("EUR", SettingsRules.normalisedCurrency("EUR"))
        assertEquals("GBP", SettingsRules.normalisedCurrency(" gbp "))
        assertNull(SettingsRules.normalisedCurrency(""))
        assertNull(SettingsRules.normalisedCurrency("EU"))
        assertNull(SettingsRules.normalisedCurrency("QQQ"))
        assertNull(SettingsRules.normalisedCurrency("EURO"))
    }

    @Test
    fun `reads a typed lesson length only when it is in range`() {
        assertEquals(45, SettingsRules.lessonMinutes("45"))
        assertEquals(1, SettingsRules.lessonMinutes(" 1 "))
        assertEquals(MAX_LESSON_MINUTES, SettingsRules.lessonMinutes("$MAX_LESSON_MINUTES"))
        assertNull(SettingsRules.lessonMinutes(""))
        assertNull(SettingsRules.lessonMinutes("0"))
        assertNull(SettingsRules.lessonMinutes("${MAX_LESSON_MINUTES + 1}"))
        assertNull(SettingsRules.lessonMinutes("1.5"))
        assertNull(SettingsRules.lessonMinutes("ten"))
    }
}
