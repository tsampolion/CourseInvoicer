package gr.eduinvoice.core.testing

import gr.eduinvoice.core.createInvoiceUseCase
import gr.eduinvoice.core.euros
import gr.eduinvoice.core.lesson
import gr.eduinvoice.core.model.DomainException
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.model.InvoicePayment
import gr.eduinvoice.core.report.Reports
import gr.eduinvoice.core.student
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The fake must fail where SQLite fails, or JVM tests pass for code that breaks on a device. */
class InMemoryRepositoriesTest {
    private val repos = InMemoryRepositories()
    private val issueDate = LocalDate.parse("2026-03-31")

    @Test
    fun `invoices block deleting their student like ON DELETE RESTRICT`() = runTest {
        val maria = repos.saveStudent(student())
        repos.createInvoiceUseCase()(maria, listOf(repos.saveLesson(lesson(maria))), issueDate)

        val error = runCatching { repos.deleteStudent(maria) }.exceptionOrNull()

        assertTrue("expected a constraint failure, got $error", error is IllegalStateException)
        assertEquals("Maria", repos.getStudent(maria)?.firstName)
    }

    @Test
    fun `invoice links are all or nothing like the SQL guard`() = runTest {
        val maria = repos.saveStudent(student())
        val free = repos.saveLesson(lesson(maria))
        val absent = repos.saveLesson(lesson(maria, absent = true))
        val invoice = Invoice(number = "2026-0001", studentId = maria, issueDate = issueDate, total = euros("20"))

        val error = runCatching { repos.createInvoice(invoice, listOf(free, absent), null) }.exceptionOrNull()

        assertTrue("expected DomainException, got $error", error is DomainException)
        assertTrue(repos.invoices.isEmpty())
        assertNull(repos.getLesson(free)?.invoiceId)
    }

    @Test
    fun `aggregates match the rules in core`() = runTest {
        val maria = repos.saveStudent(student())
        val nikos = repos.saveStudent(student(firstName = "Nikos"))
        val a = repos.saveLesson(lesson(maria, fee = "20"))
        val b = repos.saveLesson(lesson(maria, fee = "30", paidOn = "2026-03-11"))
        repos.saveLesson(lesson(nikos, fee = "50"))
        repos.saveLesson(lesson(nikos, fee = "15", absent = true))
        val invoice = repos.createInvoiceUseCase()(maria, listOf(a, b), issueDate)

        assertEquals(Reports.balances(repos.lessons), repos.observeBalances().first())
        assertEquals(mapOf(invoice.id to InvoicePayment(2, 1)), repos.observeInvoicePayments().first())
    }
}
