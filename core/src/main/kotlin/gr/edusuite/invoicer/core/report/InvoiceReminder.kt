package gr.edusuite.invoicer.core.report

import gr.edusuite.invoicer.core.model.ReminderSettings
import gr.edusuite.invoicer.core.model.ReminderState
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

/** Lessons on no statement yet within a period, and how many students they belong to. */
data class UninvoicedSummary(val lessons: Int, val students: Int)

/**
 * When to remind the tutor to send the month's statements (R15).
 *
 * A month's reminder starts on the 29th (in February, which has fewer than 30 days, on its second-to-last
 * day) and runs to the end of the month. December, June and the Easter month start on the 21st instead,
 * each while its switch is on, because lessons stop for the holidays. During the first [CARRY_OVER_DAYS]
 * days of the next month the previous month is still reminded. Whether the month has lessons left to bill
 * is the caller's check.
 */
object InvoiceReminder {
    /** The day the early months' reminder starts. */
    const val EARLY_DAY = 21

    /** The day an ordinary month's reminder starts, in a month that has one. */
    const val NORMAL_DAY = 29

    /** How many days into the next month the previous month is still reminded. */
    const val CARRY_OVER_DAYS = 7

    /**
     * Orthodox Easter Sunday of [year] as a Gregorian date: Meeus's Julian-calendar algorithm, shifted by the
     * Julian–Gregorian difference (13 days in 1900–2099, 14 in 2100–2199).
     */
    fun orthodoxEaster(year: Int): LocalDate {
        val a = year % 4
        val b = year % 7
        val c = year % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val month = (d + e + 114) / 31
        val day = (d + e + 114) % 31 + 1
        val julianToGregorian = year / 100 - year / 400 - 2
        return LocalDate.of(year, month, day).plusDays(julianToGregorian.toLong())
    }

    /**
     * The month billed early because of Easter: Easter's own month when Easter falls on or after the 21st
     * (the holidays start inside that month's reminder window), else the month before it.
     */
    fun easterMonth(year: Int): YearMonth {
        val easter = orthodoxEaster(year)
        val month = YearMonth.from(easter)
        return if (easter.dayOfMonth >= EARLY_DAY) month else month.minusMonths(1)
    }

    /** Whether [month]'s reminder starts on the [EARLY_DAY] under [settings]. */
    fun isEarly(month: YearMonth, settings: ReminderSettings): Boolean = when {
        settings.earlyDecember && month.month == Month.DECEMBER -> true
        settings.earlyJune && month.month == Month.JUNE -> true
        settings.earlyEaster && month == easterMonth(month.year) -> true
        else -> false
    }

    /** The first day of [month]'s reminder; it runs to the end of the month. */
    fun windowStart(month: YearMonth, settings: ReminderSettings): LocalDate = when {
        isEarly(month, settings) -> month.atDay(EARLY_DAY)
        month.lengthOfMonth() < 30 -> month.atEndOfMonth().minusDays(1)
        else -> month.atDay(NORMAL_DAY)
    }

    /**
     * The month to remind about on [today], or null: today's month once its reminder has started, else the
     * previous month during the first [CARRY_OVER_DAYS] days. Nothing while reminders are off, while
     * snoozed, or for a month marked done.
     */
    fun dueMonth(today: LocalDate, settings: ReminderSettings, state: ReminderState): YearMonth? {
        if (!settings.enabled) return null
        val snoozedUntil = state.snoozedUntil
        if (snoozedUntil != null && today < snoozedUntil) return null
        val current = YearMonth.from(today)
        val month = when {
            today >= windowStart(current, settings) -> current
            today.dayOfMonth <= CARRY_OVER_DAYS -> current.minusMonths(1)
            else -> return null
        }
        val doneFor = state.doneFor
        return if (doneFor != null && month <= doneFor) null else month
    }
}
