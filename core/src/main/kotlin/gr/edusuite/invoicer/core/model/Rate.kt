package gr.edusuite.invoicer.core.model

import java.math.BigDecimal
import java.math.RoundingMode

enum class RateType { HOURLY, PER_LESSON }

/** How a student or group is charged. */
data class Rate(val amount: Money, val type: RateType) {
    /** HOURLY: amount × minutes / 60, rounded half-up to the cent. PER_LESSON: the flat amount. */
    fun feeFor(durationMinutes: Int): Money {
        require(durationMinutes > 0) { "Duration must be positive" }
        return when (type) {
            RateType.PER_LESSON -> amount
            RateType.HOURLY -> Money(
                BigDecimal.valueOf(amount.cents)
                    .multiply(BigDecimal.valueOf(durationMinutes.toLong()))
                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
                    .longValueExact(),
            )
        }
    }
}
