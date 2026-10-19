package gr.edusuite.invoicer.core.privacy

import gr.edusuite.invoicer.core.model.Money
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RetentionTest {
    @Test fun dueForErasureAfterTwelveMonthsPaid() {
        val last = LocalDate.of(2025, 9, 30)
        assertFalse(dueForErasure(last, Money.ZERO, LocalDate.of(2026, 9, 30)))
        assertTrue(dueForErasure(last, Money.ZERO, LocalDate.of(2026, 10, 1)))
        assertFalse("money still owed", dueForErasure(last, Money(2000), LocalDate.of(2026, 10, 1)))
        assertFalse("no lessons, nothing to erase on this rule", dueForErasure(null, Money.ZERO, LocalDate.of(2026, 10, 1)))
    }
}
