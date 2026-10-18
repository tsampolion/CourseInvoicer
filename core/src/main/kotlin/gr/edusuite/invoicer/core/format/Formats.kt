package gr.edusuite.invoicer.core.format

import gr.edusuite.invoicer.core.model.CURRENCY_CODE
import gr.edusuite.invoicer.core.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Locale-aware formatting for money, dates and durations. */
class Formats(val locale: Locale = Locale.getDefault()) {
    private val currency: Currency = Currency.getInstance(CURRENCY_CODE)
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)

    val currencySymbol: String get() = currency.getSymbol(locale)

    fun money(amount: Money): String {
        // NumberFormat is not thread-safe, so build one per call.
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            currency = this@Formats.currency
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return format.format(BigDecimal.valueOf(amount.cents, 2))
    }

    fun date(date: LocalDate): String = dateFormatter.format(date)

    fun time(time: LocalTime): String = timeFormatter.format(time)

    /** "45 min", "1 h", "1 h 30 min". */
    fun duration(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "$rest min"
            rest == 0 -> "$hours h"
            else -> "$hours h $rest min"
        }
    }
}
