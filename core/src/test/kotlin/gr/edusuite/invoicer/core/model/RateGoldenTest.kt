package gr.edusuite.invoicer.core.model

import gr.edusuite.invoicer.core.euros
import gr.edusuite.invoicer.core.model.RateType.HOURLY
import gr.edusuite.invoicer.core.model.RateType.PER_LESSON
import gr.edusuite.invoicer.core.usecase.isValidDuration
import org.junit.Assert.*
import org.junit.Test

class RateGoldenTest {
    private data class Row(val rate: String, val type: RateType, val minutes: Int, val fee: String)

    // Hand-calculated expectations. Never regenerate this table from the billing implementation.
    private val GOLDEN = listOf(
        Row("10.00", HOURLY, 15, "2.50"), // 1000 × 15 / 60 = 250 → 250 cents
        Row("0.10", HOURLY, 15, "0.03"), // 10 × 15 / 60 = 2.5 → 3
        Row("0.10", HOURLY, 45, "0.08"), // 10 × 45 / 60 = 7.5 → 8
        Row("0.06", HOURLY, 15, "0.02"), // 6 × 15 / 60 = 1.5 → 2 (control)
        Row("0.02", HOURLY, 15, "0.01"), // 2 × 15 / 60 = 0.5 → 1
        Row("20.00", HOURLY, 1, "0.33"), // 2000 × 1 / 60 = 33.333… → 33
        Row("20.00", HOURLY, 2, "0.67"), // 2000 × 2 / 60 = 66.666… → 67
        Row("20.00", HOURLY, 720, "240.00"), // 2000 × 720 / 60 = 24000 → 24000
        Row("15.00", PER_LESSON, 1, "15.00"), // flat 1500 cents
        Row("15.00", PER_LESSON, 720, "15.00"), // flat 1500 cents
        Row("0.00", HOURLY, 60, "0.00"), // 0 × 60 / 60 = 0 → 0
        Row("100000.00", HOURLY, 720, "1200000.00"), // 10000000 × 720 / 60 = 120000000 → 120000000
        Row("25.50", HOURLY, 50, "21.25"), // 2550 × 50 / 60 = 2125 → 2125
        Row("18.00", HOURLY, 90, "27.00"), // 1800 × 90 / 60 = 2700 → 2700
        Row("12.00", HOURLY, 45, "9.00"), // 1200 × 45 / 60 = 900 → 900
        Row("33.33", HOURLY, 60, "33.33"), // 3333 × 60 / 60 = 3333 → 3333
        Row("33.33", HOURLY, 30, "16.67"), // 3333 × 30 / 60 = 1666.5 → 1667
        Row("7.77", HOURLY, 7, "0.91"), // 777 × 7 / 60 = 90.65 → 91
        Row("1.01", HOURLY, 59, "0.99"), // 101 × 59 / 60 = 99.3166… → 99
        Row("0.01", HOURLY, 29, "0.00"), // 1 × 29 / 60 = 0.4833… → 0
        Row("0.01", HOURLY, 30, "0.01"), // 1 × 30 / 60 = 0.5 → 1
        Row("100000.00", PER_LESSON, 720, "100000.00"), // flat 10000000 cents
    )

    @Test fun `billing matches the hand-written golden table`() {
        GOLDEN.forEach { row ->
            assertEquals(row.toString(), euros(row.fee), Rate(euros(row.rate), row.type).feeFor(row.minutes))
        }
    }

    @Test fun `duration boundaries are explicit`() {
        RateType.entries.forEach { type ->
            listOf(0, -1).forEach { minutes ->
                assertThrows(IllegalArgumentException::class.java) { Rate(euros("20"), type).feeFor(minutes) }
            }
        }
        assertFalse(isValidDuration(0))
        assertFalse(isValidDuration(721))
        assertTrue(isValidDuration(1))
        assertTrue(isValidDuration(720))
    }
}
