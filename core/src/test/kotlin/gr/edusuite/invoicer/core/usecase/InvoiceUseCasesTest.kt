package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.createInvoiceUseCase
import gr.edusuite.invoicer.core.euros
import gr.edusuite.invoicer.core.invoice.InvoiceDocumentBuilder
import gr.edusuite.invoicer.core.lateCancel
import gr.edusuite.invoicer.core.lesson
import gr.edusuite.invoicer.core.model.BusinessProfile
import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.renderer
import gr.edusuite.invoicer.core.hourly
import gr.edusuite.invoicer.core.student
import gr.edusuite.invoicer.core.testing.InMemoryRepositories
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InvoiceUseCasesTest {
    private val repos = InMemoryRepositories()
    private val createInvoice = repos.createInvoiceUseCase()
    private val getDocument = GetInvoiceDocument(repos, repos, repos.renderer())
    private val issueDate = LocalDate.parse("2026-03-31")

    @Test
    fun `creates a numbered invoice and links the lessons`() = runTest {
        val maria = repos.saveStudent(student())
        val first = repos.saveLesson(lesson(maria, fee = "20"))
        val second = repos.saveLesson(lesson(maria, fee = "30", minutes = 90))

        val invoice = createInvoice(maria, listOf(first, second), issueDate, notes = " March ")

        assertEquals("2026-0001", invoice.number)
        assertEquals(euros("50"), invoice.total)
        assertEquals("March", invoice.notes)
        assertEquals(invoice, repos.getInvoice(invoice.id))
        assertTrue(repos.lessons.all { it.invoiceId == invoice.id && !it.isPaid })
    }

    @Test
    fun `marking paid keeps earlier payment dates`() = runTest {
        val maria = repos.saveStudent(student())
        val paidEarlier = repos.saveLesson(lesson(maria, paidOn = "2026-03-05"))
        val unpaid = repos.saveLesson(lesson(maria))

        createInvoice(maria, listOf(paidEarlier, unpaid), issueDate, markPaid = true)

        assertEquals(LocalDate.parse("2026-03-05"), repos.getLesson(paidEarlier)?.paidOn)
        assertEquals(issueDate, repos.getLesson(unpaid)?.paidOn)
    }

    @Test
    fun `numbers continue within the year`() = runTest {
        val maria = repos.saveStudent(student())
        val a = repos.saveLesson(lesson(maria))
        val b = repos.saveLesson(lesson(maria))

        createInvoice(maria, listOf(a), issueDate)
        val second = createInvoice(maria, listOf(b), issueDate)

        assertEquals("2026-0002", second.number)
    }

    @Test
    fun `rejects invalid selections`() = runTest {
        val maria = repos.saveStudent(student())
        val nikos = repos.saveStudent(student(firstName = "Nikos"))
        val ok = repos.saveLesson(lesson(maria))
        val other = repos.saveLesson(lesson(nikos))

        assertRejected("Select at least one lesson") { createInvoice(maria, emptyList(), issueDate) }
        assertRejected("same student") { createInvoice(maria, listOf(ok, other), issueDate) }
        assertRejected("no longer exist") { createInvoice(maria, listOf(ok, 999), issueDate) }

        createInvoice(maria, listOf(ok), issueDate)
        assertRejected("already on a statement") { createInvoice(maria, listOf(ok), issueDate) }
        assertEquals(1, repos.invoices.size)
    }

    @Test
    fun `statements take absences, excused ones at 0 and late cancellations at the fee`() = runTest {
        val maria = repos.saveStudent(student())
        val held = repos.saveLesson(lesson(maria, date = "2026-03-02", fee = "20"))
        val excused = repos.saveLesson(lesson(maria, date = "2026-03-09", fee = "20", absent = true, absenceNote = "ill"))
        val late = repos.saveLesson(lateCancel(maria, fee = "20").copy(date = LocalDate.parse("2026-03-16")))

        val invoice = createInvoice(maria, listOf(held, excused, late), issueDate, markPaid = true)

        assertEquals(euros("40"), invoice.total)
        assertEquals(listOf("€20.00", "€0.00", "€20.00"), invoice.document!!.lines.map { it.amount })
        assertEquals(
            listOf(null, "Absent — not charged: ill", "Late cancellation (under 24 h) — charged"),
            invoice.document!!.lines.map { it.note },
        )
        assertTrue(repos.lessons.all { it.invoiceId == invoice.id })
        assertEquals("charged lessons are marked paid", issueDate, repos.getLesson(late)?.paidOn)
        assertNull("an excused absence is never paid", repos.getLesson(excused)?.paidOn)
        assertTrue(getDocument(invoice.id).paid)
    }

    @Test
    fun `a statement of excused absences totals 0 and is paid at once`() = runTest {
        val maria = repos.saveStudent(student())
        val a = repos.saveLesson(lesson(maria, absent = true))
        val b = repos.saveLesson(lesson(maria, absent = true, date = "2026-03-17"))

        val invoice = createInvoice(maria, listOf(a, b), issueDate)

        assertEquals(euros("0"), invoice.total)
        assertEquals("€0.00", invoice.document!!.total)
        assertEquals(InvoiceDocumentBuilder.DISCLAIMER, invoice.document!!.disclaimer)
        assertTrue(getDocument(invoice.id).paid)
        assertTrue(repos.lessons.none { it.isPaid })
    }

    @Test
    fun `a statement with an unpaid late cancellation is not paid until it is`() = runTest {
        val maria = repos.saveStudent(student())
        val excused = repos.saveLesson(lesson(maria, absent = true))
        val late = repos.saveLesson(lateCancel(maria))
        val invoice = createInvoice(maria, listOf(excused, late), issueDate)

        assertFalse(getDocument(invoice.id).paid)
        repos.setPaid(listOf(excused, late), LocalDate.parse("2026-04-02"))

        assertTrue(getDocument(invoice.id).paid)
        assertNull("setPaid skips the excused absence", repos.getLesson(excused)?.paidOn)
    }

    @Test
    fun `voiding an invoice releases its lessons and retires its number`() = runTest {
        val maria = repos.saveStudent(student())
        val id = repos.saveLesson(lesson(maria))
        val invoice = createInvoice(maria, listOf(id), issueDate)
        val voidDate = LocalDate.parse("2026-04-02")

        VoidInvoice(repos)(invoice.id, voidDate)

        assertEquals(voidDate, repos.getInvoice(invoice.id)?.voidedOn)
        assertNull(repos.getLesson(id)?.invoiceId)
        assertEquals("2026-0002", createInvoice(maria, listOf(id), issueDate).number)
    }

    @Test
    fun `the issued document never changes, only its paid status does`() = runTest {
        val maria = repos.saveStudent(student())
        val groupId = repos.saveGroup(StudentGroup(name = "B1", rate = hourly("12"), memberIds = setOf(maria)))
        val lessonId = repos.saveLesson(lesson(maria).copy(groupId = groupId, notes = "Unit 4"))
        repos.settingsRepository.update { it.copy(business = BusinessProfile(name = "Tutor A", iban = "GR01")) }
        val invoice = createInvoice(maria, listOf(lessonId), issueDate)
        val issued = getDocument(invoice.id)
        assertEquals(invoice.document, issued)
        assertEquals(listOf("Tutor A"), issued.seller)
        assertEquals("Group lesson · B1 — Unit 4", issued.lines.single().description)
        assertFalse(issued.paid)

        // Everything a re-render would read changes (R12, D10)...
        repos.settingsRepository.update {
            it.copy(currencyCode = "GBP", business = BusinessProfile(name = "Tutor B"), invoiceFooter = "New")
        }
        repos.saveStudent(repos.getStudent(maria)!!.copy(firstName = "Renamed"))
        repos.saveGroup(repos.getGroup(groupId)!!.copy(name = "Renamed group"))
        SaveLesson(repos)(repos.getLesson(lessonId)!!.copy(notes = "Edited"))

        // ...but the shared document is the one the parent already holds; only the payment is live.
        assertEquals(issued, getDocument(invoice.id))
        repos.setPaid(listOf(lessonId), LocalDate.parse("2026-04-05"))
        assertEquals(issued.copy(paid = true), getDocument(invoice.id))
    }

    @Test
    fun `invoices issued before documents were stored are frozen when first shared`() = runTest {
        val maria = repos.saveStudent(student())
        val lessonId = repos.saveLesson(lesson(maria))
        val legacy = Invoice(number = "2026-0001", studentId = maria, issueDate = issueDate, total = euros("20"))
        val id = repos.createInvoice(legacy, listOf(lessonId), paidOn = null)
        repos.settingsRepository.update { it.copy(business = BusinessProfile(name = "Tutor A")) }

        val first = getDocument(id)
        assertNotNull(repos.getInvoice(id)?.document)
        repos.settingsRepository.update { it.copy(business = BusinessProfile(name = "Tutor B")) }

        assertEquals(listOf("Tutor A"), first.seller)
        assertEquals(first, getDocument(id))
    }

    @Test
    fun `void invoices cannot be shared`() = runTest {
        val maria = repos.saveStudent(student())
        val invoice = createInvoice(maria, listOf(repos.saveLesson(lesson(maria))), issueDate)
        VoidInvoice(repos)(invoice.id, issueDate)

        assertRejected("void") { getDocument(invoice.id) }
        assertRejected("not found") { getDocument(999) }
    }

    @Test
    fun `void is rejected twice and for unknown invoices`() = runTest {
        val maria = repos.saveStudent(student())
        val invoice = createInvoice(maria, listOf(repos.saveLesson(lesson(maria))), issueDate)
        val voidInvoice = VoidInvoice(repos)
        voidInvoice(invoice.id, issueDate)

        assertRejected("already void") { voidInvoice(invoice.id, issueDate) }
        assertRejected("not found") { voidInvoice(999, issueDate) }
    }
}

internal suspend fun assertRejected(messagePart: String, block: suspend () -> Unit) {
    val error = runCatching { block() }.exceptionOrNull()
    assertTrue("expected DomainException, got $error", error is DomainException)
    assertTrue("'${error?.message}' should mention '$messagePart'", error!!.message!!.contains(messagePart))
}

