package gr.edusuite.invoicer.core.invoice

import gr.edusuite.invoicer.core.model.DomainException
import java.util.Locale

/** Invoice numbers are sequential per calendar year: 2026-0001, 2026-0002, ... */
object InvoiceNumbering {
    private val pattern = Regex("""^(\d{4})-(\d{4,9})$""")

    fun next(year: Int, existingNumbers: Collection<String>): String {
        val last = existingNumbers
            .mapNotNull { pattern.matchEntire(it.trim()) }
            .filter { it.groupValues[1].toInt() == year }
            .mapNotNull { it.groupValues[2].toLongOrNull() }
            .maxOrNull()
            ?: 0L
        if (last >= 999_999_999L) throw DomainException("This year has run out of statement numbers")
        return String.format(Locale.ROOT, "%04d-%04d", year, last + 1)
    }
}
