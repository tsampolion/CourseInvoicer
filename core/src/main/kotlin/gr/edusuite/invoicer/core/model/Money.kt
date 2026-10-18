package gr.edusuite.invoicer.core.model

import java.math.BigDecimal

/** An amount of money in minor units (cents). All amounts are euro cents. */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {
    operator fun plus(other: Money): Money = Money(cents + other.cents)

    operator fun minus(other: Money): Money = Money(cents - other.cents)

    operator fun times(factor: Int): Money = Money(cents * factor)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    val isPositive: Boolean get() = cents > 0

    /** Decimal text without currency, e.g. "12.50", suitable for an editable field. */
    fun toPlainString(): String = BigDecimal.valueOf(cents, 2).toPlainString()

    companion object {
        val ZERO = Money(0)

        /**
         * Parses user input such as "12", "12.5" or "12,50".
         * Returns null for blank, malformed, negative input or more than two decimals.
         */
        fun parse(text: String): Money? {
            val normalized = text.trim().replace(',', '.')
            if (normalized.isEmpty()) return null
            val value = normalized.toBigDecimalOrNull() ?: return null
            if (value.signum() < 0 || value.stripTrailingZeros().scale() > 2) return null
            return runCatching { Money(value.movePointRight(2).longValueExact()) }.getOrNull()
        }
    }
}

inline fun <T> Iterable<T>.sumOfMoney(selector: (T) -> Money): Money =
    fold(Money.ZERO) { total, item -> total + selector(item) }
