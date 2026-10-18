package gr.edusuite.invoicer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OverdueTest {
    private val oct1 = LocalDate.of(2026, 10, 1)
    private val oct2 = LocalDate.of(2026, 10, 2)
    private val dec1 = LocalDate.of(2026, 12, 1)

    private fun invoice(issue: LocalDate, voidedOn: LocalDate? = null) =
        Invoice(number = "2026-0001", studentId = 1, issueDate = issue, total = Money(2000), voidedOn = voidedOn)

    @Test fun dueOnTheLastDayOfTheIssueMonth() {
        assertEquals(LocalDate.of(2026, 10, 31), dueDate(LocalDate.of(2026, 10, 12)))
        assertEquals(LocalDate.of(2028, 2, 29), dueDate(LocalDate.of(2028, 2, 1)))
    }

    @Test fun overdueFlipsOnFirstOfNextMonth() {
        val inv = invoice(issue = LocalDate.of(2026, 10, 31))
        assertFalse(isOverdue(inv, InvoicePayment(2, 1), LocalDate.of(2026, 10, 31)))
        assertTrue(isOverdue(inv, InvoicePayment(2, 1), LocalDate.of(2026, 11, 1)))
    }

    @Test fun voidIsNeverOverdue() {
        assertFalse(isOverdue(invoice(issue = oct1, voidedOn = oct2), InvoicePayment(1, 1), dec1))
    }

    @Test fun excusedOnlyStatementIsPaid() {
        assertFalse(isOverdue(invoice(issue = oct1), InvoicePayment(1, 0), dec1))
    }

    @Test fun paidOrEmptyIsNeverOverdue() {
        assertFalse(isOverdue(invoice(issue = oct1), InvoicePayment(3, 0), dec1))
        assertFalse("no linked lessons, nothing to pay", isOverdue(invoice(issue = oct1), null, dec1))
    }
}
