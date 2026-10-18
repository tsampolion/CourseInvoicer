package gr.edusuite.invoicer.core.invoice

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertThrows
import gr.edusuite.invoicer.core.model.DomainException

class InvoiceNumberingTest {
    @Test
    fun `first invoice of the year`() {
        assertEquals("2026-0001", InvoiceNumbering.next(2026, emptyList()))
    }

    @Test
    fun `continues the sequence of the same year only`() {
        val existing = listOf("2026-0001", "2026-0007", "2025-0099", "draft", "2026-12")
        assertEquals("2026-0008", InvoiceNumbering.next(2026, existing))
        assertEquals("2027-0001", InvoiceNumbering.next(2027, existing))
    }

    @Test
    fun `grows past four digits`() {
        assertEquals("2026-10000", InvoiceNumbering.next(2026, listOf("2026-9999")))
    }
    @Test fun `trims numbers and ignores malformed numbers and other years`() {
        assertEquals("2026-0001", InvoiceNumbering.next(2026, listOf("2025-9999", "2026-12", "x2026-0009", "2026-0009x")))
        assertEquals("2026-0010", InvoiceNumbering.next(2026, listOf(" 2026-0009 ")))
    }

    @Test fun `refuses to run beyond nine digits`() {
        val error = assertThrows(DomainException::class.java) {
            InvoiceNumbering.next(2026, listOf("2026-999999999"))
        }
        assertEquals("This year has run out of statement numbers", error.message)
    }

}
