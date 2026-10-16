package gr.edusuite.invoicer.core.report

import gr.edusuite.invoicer.core.model.ReminderSettings
import gr.edusuite.invoicer.core.model.ReminderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

class InvoiceReminderTest {
    private val defaults = ReminderSettings()
    private val noEarlyMonths = ReminderSettings(earlyDecember = false, earlyJune = false, earlyEaster = false)

    private fun date(text: String) = LocalDate.parse(text)

    private fun month(text: String) = YearMonth.parse(text)

    private fun due(today: String, settings: ReminderSettings = defaults, state: ReminderState = ReminderState()) =
        InvoiceReminder.dueMonth(date(today), settings, state)

    @Test
    fun `orthodox Easter matches the published dates`() {
        val published = mapOf(
            2010 to "2010-04-04",
            2016 to "2016-05-01",
            2019 to "2019-04-28",
            2020 to "2020-04-19",
            2021 to "2021-05-02",
            2022 to "2022-04-24",
            2023 to "2023-04-16",
            2024 to "2024-05-05",
            2025 to "2025-04-20",
            2026 to "2026-04-12",
            2027 to "2027-05-02",
            2028 to "2028-04-16",
            2029 to "2029-04-08",
            2030 to "2030-04-28",
            2031 to "2031-04-13",
            2032 to "2032-05-02",
            2034 to "2034-04-09",
            2035 to "2035-04-29",
        )
        published.forEach { (year, easter) ->
            assertEquals("$year", date(easter), InvoiceReminder.orthodoxEaster(year))
        }
    }

    @Test
    fun `orthodox Easter is always a Sunday between 4 April and 8 May`() {
        for (year in 1900..2099) {
            val easter = InvoiceReminder.orthodoxEaster(year)
            assertEquals("$year", DayOfWeek.SUNDAY, easter.dayOfWeek)
            assertTrue("$year: $easter", easter in LocalDate.of(year, 4, 4)..LocalDate.of(year, 5, 8))
        }
        // From March 2100 the calendars are 14 days apart.
        assertEquals(DayOfWeek.SUNDAY, InvoiceReminder.orthodoxEaster(2100).dayOfWeek)
    }

    @Test
    fun `the Easter month is Easter's month from the 21st on, else the month before`() {
        val expected = mapOf(
            2021 to "2021-04", // Easter 2 May
            2022 to "2022-04", // 24 April
            2023 to "2023-03", // 16 April
            2024 to "2024-04", // 5 May
            2025 to "2025-03", // 20 April
            2026 to "2026-03", // 12 April
            2027 to "2027-04", // 2 May
            2028 to "2028-03", // 16 April
            2029 to "2029-03", // 8 April
            2030 to "2030-04", // 28 April
        )
        expected.forEach { (year, easterMonth) ->
            assertEquals("$year", month(easterMonth), InvoiceReminder.easterMonth(year))
        }
    }

    @Test
    fun `an ordinary month starts on the 29th and February on its second-to-last day`() {
        assertEquals(date("2026-01-29"), InvoiceReminder.windowStart(month("2026-01"), defaults))
        assertEquals("30-day month", date("2026-04-29"), InvoiceReminder.windowStart(month("2026-04"), defaults))
        assertEquals(date("2026-11-29"), InvoiceReminder.windowStart(month("2026-11"), defaults))
        assertEquals(date("2025-02-27"), InvoiceReminder.windowStart(month("2025-02"), defaults))
        assertEquals("leap year", date("2028-02-28"), InvoiceReminder.windowStart(month("2028-02"), defaults))
    }

    @Test
    fun `December, June and the Easter month start on the 21st while their switches are on`() {
        fun start(text: String, settings: ReminderSettings = defaults) =
            InvoiceReminder.windowStart(month(text), settings)

        assertEquals(date("2026-12-21"), start("2026-12"))
        assertEquals(date("2026-06-21"), start("2026-06"))
        assertEquals("Easter 12 April", date("2026-03-21"), start("2026-03"))
        assertEquals("not the Easter month", date("2026-04-29"), start("2026-04"))
        assertEquals("Easter 28 April", date("2030-04-21"), start("2030-04"))
        assertEquals(date("2030-03-29"), start("2030-03"))

        listOf("2026-12", "2026-06", "2026-03", "2030-04").forEach {
            assertEquals("$it, switches off", month(it).atDay(29), start(it, noEarlyMonths))
        }
        // Each switch moves only its own month.
        val onlyJune = noEarlyMonths.copy(earlyJune = true)
        assertTrue(InvoiceReminder.isEarly(month("2026-06"), onlyJune))
        assertFalse(InvoiceReminder.isEarly(month("2026-12"), onlyJune))
        assertFalse(InvoiceReminder.isEarly(month("2026-03"), onlyJune))
    }

    @Test
    fun `the current month is due from its window to its last day`() {
        assertNull(due("2026-01-28"))
        assertEquals(month("2026-01"), due("2026-01-29"))
        assertEquals(month("2026-01"), due("2026-01-31"))
        assertNull(due("2026-03-20"))
        assertEquals("Easter month", month("2026-03"), due("2026-03-21"))
        assertEquals(month("2025-02"), due("2025-02-27"))
        assertNull(due("2028-02-27"))
        assertEquals(month("2028-02"), due("2028-02-29"))
    }

    @Test
    fun `the previous month stays due for the first seven days`() {
        assertEquals(month("2026-03"), due("2026-04-01"))
        assertEquals(month("2026-03"), due("2026-04-07"))
        assertNull(due("2026-04-08"))
        assertEquals("across the year end", month("2026-12"), due("2027-01-03"))
    }

    @Test
    fun `a month marked done is not reminded again, nor are earlier ones`() {
        val doneMarch = ReminderState(doneFor = month("2026-03"))

        assertNull(due("2026-03-30", state = doneMarch))
        assertNull("carry-over", due("2026-04-02", state = doneMarch))
        assertNull(due("2026-03-30", state = ReminderState(doneFor = month("2026-04"))))
        assertEquals("the next month is due again", month("2026-04"), due("2026-04-29", state = doneMarch))
    }

    @Test
    fun `remind me tomorrow hides the reminder until the next day`() {
        val snoozed = ReminderState(snoozedUntil = date("2026-03-30"))

        assertNull(due("2026-03-29", state = snoozed))
        assertEquals(month("2026-03"), due("2026-03-30", state = snoozed))
        assertEquals(month("2026-03"), due("2026-04-01", state = snoozed))
    }

    @Test
    fun `the master switch turns every reminder off`() {
        val off = ReminderSettings(enabled = false)

        assertNull(due("2026-01-30", settings = off))
        assertNull(due("2026-12-22", settings = off))
        assertNull(due("2026-02-03", settings = off))
    }

    @Test
    fun `an early month with its switch off waits for the 29th`() {
        val noDecember = defaults.copy(earlyDecember = false)

        assertNull(due("2026-12-21", settings = noDecember))
        assertEquals(month("2026-12"), due("2026-12-29", settings = noDecember))
        assertEquals(Month.DECEMBER, due("2026-12-21")?.month)
    }
}
