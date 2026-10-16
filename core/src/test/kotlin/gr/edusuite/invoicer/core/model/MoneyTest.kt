package gr.edusuite.invoicer.core.model

import gr.edusuite.invoicer.core.hourly
import gr.edusuite.invoicer.core.perLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun `parses decimal input with dot or comma`() {
        assertEquals(Money(1200), Money.parse("12"))
        assertEquals(Money(1250), Money.parse("12.5"))
        assertEquals(Money(1250), Money.parse(" 12,50 "))
        assertEquals(Money(1250), Money.parse("12.500"))
        assertEquals(Money(1), Money.parse("0.01"))
    }

    @Test
    fun `rejects invalid input`() {
        listOf("", " ", "abc", "-1", "1.234", "1.2.3", "99999999999999999999").forEach {
            assertNull("'$it' should be rejected", Money.parse(it))
        }
    }

    @Test
    fun `formats as plain decimal text`() {
        assertEquals("12.50", Money(1250).toPlainString())
        assertEquals("0.05", Money(5).toPlainString())
    }

    @Test
    fun `hourly fee is prorated and rounded half up`() {
        assertEquals(Money(3000), hourly("20").feeFor(90))
        assertEquals(Money(1875), hourly("25").feeFor(45))
        assertEquals(Money(833), hourly("10").feeFor(50))
        assertEquals(Money(8), Rate(Money(10), RateType.HOURLY).feeFor(45))
    }

    @Test
    fun `per lesson fee ignores duration`() {
        assertEquals(Money(1500), perLesson("15").feeFor(90))
        assertEquals(Money(1500), perLesson("15").feeFor(30))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fee needs a positive duration`() {
        hourly("20").feeFor(0)
    }

    @Test
    fun `excused absences are never charged or outstanding but can go on a statement`() {
        val absent = gr.edusuite.invoicer.core.lesson(studentId = 1, absent = true)
        assertEquals(Money.ZERO, absent.charge)
        assertEquals(false, absent.isOutstanding)
        assertEquals(true, absent.isInvoiceable)
        assertEquals(false, absent.copy(invoiceId = 3).isInvoiceable)
    }

    @Test
    fun `late cancellations are charged the full fee`() {
        val late = gr.edusuite.invoicer.core.lateCancel(studentId = 1, fee = "20")
        assertEquals(Money(2000), late.charge)
        assertEquals(true, late.isOutstanding)
        assertEquals(true, late.isLateCancellation)
        assertEquals(false, late.isExcused)
        assertEquals(
            "the charge means nothing for a lesson that was held",
            Money(2000),
            late.copy(absent = false).charge,
        )
    }
}
