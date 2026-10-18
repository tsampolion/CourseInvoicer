package gr.edusuite.invoicer.core.format

import gr.edusuite.invoicer.core.model.Money
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class FormatsTest {
    @Test fun `UK amounts use euro and two decimals`() {
        assertEquals("€1,234.56", Formats(Locale.UK).money(Money(123456)))
        assertEquals("€0.01", Formats(Locale.UK).money(Money(1)))
    }

    @Test fun `Greek amounts and dates use local separators and month names`() {
        val formats = Formats(Locale.forLanguageTag("el-GR"))
        val amount = formats.money(Money(123456)).replace('\u00a0', ' ').replace('\u202f', ' ')
        assertTrue(amount, amount.contains("1.234,56"))
        assertTrue(amount, amount.contains("€"))
        val date = formats.date(LocalDate.of(2026, 3, 9))
        assertTrue(date, date.contains("2026"))
        assertTrue(date, date.contains("Μαρ", ignoreCase = true) || date.contains("Μάρ", ignoreCase = true))
    }

    @Test fun `duration formatting covers the full supported range`() {
        val formats = Formats(Locale.UK)
        mapOf(1 to "1 min", 60 to "1 h", 90 to "1 h 30 min", 720 to "12 h").forEach { (minutes, text) ->
            assertEquals(text, formats.duration(minutes))
        }
    }
}
