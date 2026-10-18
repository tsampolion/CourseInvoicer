package gr.edusuite.invoicer.core.invoice

import gr.edusuite.invoicer.core.euros
import gr.edusuite.invoicer.core.format.Formats
import gr.edusuite.invoicer.core.lateCancel
import gr.edusuite.invoicer.core.lesson
import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.BusinessProfile
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.InvoiceDocument
import gr.edusuite.invoicer.core.student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class InvoiceDocumentTest {
    private val formats = Formats(Locale.UK)
    private val settings = AppSettings(
        business = BusinessProfile(
            name = "Dimitris Tutoring",
            address = "1 Main St\nAthens",
            phone = "2100000000",
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
        assertEquals("ΕΝΗΜΕΡΩΤΙΚΗ ΚΑΤΑΣΤΑΣΗ ΜΑΘΗΜΑΤΩΝ / Lesson statement", doc.title)
        assertEquals(
            "ΤΟ ΠΑΡΟΝ ΔΕΝ ΑΠΟΤΕΛΕΙ ΦΟΡΟΛΟΓΙΚΟ ΣΤΟΙΧΕΙΟ. Δεν είναι τιμολόγιο ούτε απόδειξη παροχής υπηρεσιών " +
                "κατά τον ν. 4308/2014 (ΕΛΠ) και δεν διαβιβάζεται στην ΑΑΔΕ (myDATA). Για κάθε πληρωμή εκδίδεται " +
                "χωριστά νόμιμη απόδειξη.",
            doc.disclaimer,
        )
        assertEquals(
            "the address stays off statements unless the tutor opts in",
            listOf("Dimitris Tutoring", "2100000000 · d@example.com"),
            doc.seller,
        )
        assertFalse("no Tax ID line", doc.seller.any { "Tax ID" in it })
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
    fun `prints the address when the tutor opts in`() {
        val withAddress = settings.copy(showAddressOnStatements = true)

        val doc = InvoiceDocumentBuilder.build(invoice, maria, listOf(lesson(1)), emptyMap(), withAddress, formats)

        assertEquals(listOf("Dimitris Tutoring", "1 Main St", "Athens", "2100000000 · d@example.com"), doc.seller)
    }

    @Test
    fun `absence lines show what is charged and why`() {
        val lessons = listOf(
            lesson(studentId = 1, date = "2026-03-02", fee = "20", absent = true),
            lesson(studentId = 1, date = "2026-03-09", fee = "20", absent = true, absenceNote = " ill "),
            lateCancel(studentId = 1, fee = "20").copy(date = LocalDate.parse("2026-03-16")),
            lateCancel(studentId = 1, fee = "20", note = "called at 16:00").copy(date = LocalDate.parse("2026-03-23")),
        )

        val doc = InvoiceDocumentBuilder.build(
            invoice.copy(total = euros("40")),
            maria,
            lessons,
            emptyMap(),
            settings,
            formats,
        )

        assertEquals(
            listOf(
                InvoiceDocument.Line("2 Mar 2026", "Lesson", "1 h", "€0.00", "Absent — not charged"),
                InvoiceDocument.Line("9 Mar 2026", "Lesson", "1 h", "€0.00", "Absent — not charged: ill"),
                InvoiceDocument.Line(
                    "16 Mar 2026",
                    "Lesson",
                    "1 h",
                    "€20.00",
                    "Late cancellation (under 24 h) — charged",
                ),
                InvoiceDocument.Line(
                    "23 Mar 2026",
                    "Lesson",
                    "1 h",
                    "€20.00",
                    "Late cancellation (under 24 h) — charged: called at 16:00",
                ),
            ),
            doc.lines,
        )
        assertEquals("€40.00", doc.total)
    }

    @Test
    fun `paid when every charged lesson is paid`() {
        val lessons = listOf(lesson(studentId = 1, paidOn = "2026-03-31"), lesson(studentId = 1, absent = true))
        assertTrue(InvoiceDocumentBuilder.build(invoice, maria, lessons, emptyMap(), settings, formats).paid)

        val excusedOnly = listOf(lesson(studentId = 1, absent = true))
        assertTrue(InvoiceDocumentBuilder.build(invoice, maria, excusedOnly, emptyMap(), settings, formats).paid)

        val lateUnpaid = listOf(lateCancel(studentId = 1))
        assertFalse(InvoiceDocumentBuilder.build(invoice, maria, lateUnpaid, emptyMap(), settings, formats).paid)
    }

    @Test
    fun `stored form keeps everything but the live paid flag`() {
        val lessons = listOf(
            lesson(studentId = 1, paidOn = "2026-03-31").copy(notes = "\"quoted\" \\ ☺"),
            lesson(studentId = 1, absent = true, absenceNote = "ill"),
        )
        val doc = InvoiceDocumentBuilder.build(invoice, maria, lessons, emptyMap(), settings, formats)
        assertTrue(doc.paid)

        assertEquals(doc.copy(paid = false), InvoiceDocumentCodec.decode(InvoiceDocumentCodec.encode(doc)))
    }

    @Test
    fun `documents stored before statements decode unchanged, without title, disclaimer or notes`() {
        // As stored by 2.0.0-alpha03: no title, disclaimer or line notes.
        val stored = """
            {
              "number": "2026-0001",
              "issueDate": "31 Mar 2026",
              "seller": ["Tutor"],
              "billTo": ["Maria"],
              "lines": [{"date": "2 Mar 2026", "description": "Lesson", "duration": "1 h", "amount": "€20.00"}],
              "total": "€20.00",
              "notes": []
            }
        """.trimIndent()

        val doc = InvoiceDocumentCodec.decode(stored)

        assertNull(doc.title)
        assertNull(doc.disclaimer)
        assertNull(doc.lines.single().note)
        assertEquals("€20.00", doc.lines.single().amount)
        val reEncoded = InvoiceDocumentCodec.encode(doc)
        assertFalse("an old document is stored without the new keys", "title" in reEncoded || "note\"" in reEncoded)
    }

    @Test
    fun `durations read naturally`() {
        assertEquals("45 min", formats.duration(45))
        assertEquals("2 h", formats.duration(120))
        assertEquals("1 h 15 min", formats.duration(75))
    }
}
