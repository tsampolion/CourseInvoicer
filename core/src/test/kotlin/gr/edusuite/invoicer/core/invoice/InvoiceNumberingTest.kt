package gr.edusuite.invoicer.core.invoice

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
