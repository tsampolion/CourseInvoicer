package gr.eduinvoice.core.usecase

import gr.eduinvoice.core.euros
import gr.eduinvoice.core.lesson
import gr.eduinvoice.core.model.DomainException
import gr.eduinvoice.core.student
import gr.eduinvoice.core.testing.InMemoryRepositories
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InvoiceUseCasesTest {
    private val repos = InMemoryRepositories()
    private val createInvoice = CreateInvoice(repos, repos)
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
        val absent = repos.saveLesson(lesson(maria, absent = true))
        val other = repos.saveLesson(lesson(nikos))

        assertRejected("Select at least one lesson") { createInvoice(maria, emptyList(), issueDate) }
        assertRejected("Absences cannot be invoiced") { createInvoice(maria, listOf(ok, absent), issueDate) }
        assertRejected("same student") { createInvoice(maria, listOf(ok, other), issueDate) }
        assertRejected("no longer exist") { createInvoice(maria, listOf(ok, 999), issueDate) }

        createInvoice(maria, listOf(ok), issueDate)
        assertRejected("already on an invoice") { createInvoice(maria, listOf(ok), issueDate) }
    }

    @Test
    fun `deleting an invoice releases its lessons`() = runTest {
        val maria = repos.saveStudent(student())
        val id = repos.saveLesson(lesson(maria))
        val invoice = createInvoice(maria, listOf(id), issueDate)

        repos.deleteInvoice(invoice.id)

        assertNull(repos.getInvoice(invoice.id))
        assertNull(repos.getLesson(id)?.invoiceId)
        assertEquals("2026-0001", createInvoice(maria, listOf(id), issueDate).number)
    }
}

internal suspend fun assertRejected(messagePart: String, block: suspend () -> Unit) {
    val error = runCatching { block() }.exceptionOrNull()
    assertTrue("expected DomainException, got $error", error is DomainException)
    assertTrue("'${error?.message}' should mention '$messagePart'", error!!.message!!.contains(messagePart))
}

