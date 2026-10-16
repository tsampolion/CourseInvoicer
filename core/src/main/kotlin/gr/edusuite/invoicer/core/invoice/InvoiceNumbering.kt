package gr.edusuite.invoicer.core.invoice

import java.util.Locale

/** Invoice numbers are sequential per calendar year: 2026-0001, 2026-0002, ... */
object InvoiceNumbering {
    private val pattern = Regex("""^(\d{4})-(\d{4,9})$""")

    fun next(year: Int, existingNumbers: Collection<String>): String {
        val last = existingNumbers
            .mapNotNull { pattern.matchEntire(it.trim()) }
            .filter { it.groupValues[1].toInt() == year }
            .maxOfOrNull { it.groupValues[2].toInt() }
            ?: 0
        return String.format(Locale.ROOT, "%04d-%04d", year, last + 1)
    }
}
