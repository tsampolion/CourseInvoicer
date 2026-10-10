package gr.eduinvoice.core.invoice

import gr.eduinvoice.core.euros
import gr.eduinvoice.core.format.Formats
import gr.eduinvoice.core.lesson
import gr.eduinvoice.core.model.AppSettings
import gr.eduinvoice.core.model.BusinessProfile
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class InvoiceDocumentTest {
    private val formats = Formats(Locale.UK, "EUR")
    private val settings = AppSettings(
        business = BusinessProfile(
            name = "Dimitris Tutoring",
            address = "1 Main St\nAthens",
            taxId = "123456789",
            email = "d@example.com",
            iban = "GR16 0110 1250",
        ),
        invoiceFooter = "Thank you!",
    )
    private val maria = student(id = 1).copy(guardianName = "Eleni", phone = "6900000000")
    private val invoice = Invoice(
        id = 9,
        number = "2026-0003",
        studentId = 1,
        issueDate = LocalDate.parse("2026-03-31"),
        total = euros("38"),
        notes = "March lessons",
    )

    @Test
    fun `formats header, lines and notes`() {
        val lessons = listOf(
            lesson(studentId = 1, date = "2026-03-20", fee = "18", minutes = 90).copy(groupId = 5, notes = "Unit 4"),
            lesson(studentId = 1, date = "2026-03-02", fee = "20"),
        )

        val doc = InvoiceDocumentBuilder.build(invoice, maria, lessons, mapOf(5L to "B1 English"), settings, formats)

        assertEquals("2026-0003", doc.number)
        assertEquals("31 Mar 2026", doc.issueDate)
        assertEquals(
            listOf("Dimitris Tutoring", "1 Main St", "Athens", "Tax ID: 123456789", "d@example.com"),
            doc.seller,
        )
        assertEquals(listOf("Maria Papadopoulou", "c/o Eleni", "6900000000"), doc.billTo)
        assertEquals(
            listOf(
                InvoiceDocument.Line("2 Mar 2026", "Lesson", "1 h", "€20.00"),
                InvoiceDocument.Line("20 Mar 2026", "Group lesson · B1 English — Unit 4", "1 h 30 min", "€18.00"),
            ),
            doc.lines,
        )
        assertEquals("€38.00", doc.total)
        assertEquals(
            listOf("March lessons", "Payment by bank transfer to IBAN GR16 0110 1250", "Thank you!"),
            doc.notes,
        )
        assertFalse(doc.paid)
    }

    @Test
    fun `paid when every lesson is paid`() {
        val lessons = listOf(lesson(studentId = 1, paidOn = "2026-03-31"))
        assertTrue(InvoiceDocumentBuilder.build(invoice, maria, lessons, emptyMap(), settings, formats).paid)
    }

    @Test
    fun `durations read naturally`() {
        assertEquals("45 min", formats.duration(45))
        assertEquals("2 h", formats.duration(120))
        assertEquals("1 h 15 min", formats.duration(75))
    }
}
