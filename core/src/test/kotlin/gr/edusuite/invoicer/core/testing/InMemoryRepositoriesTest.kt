package gr.edusuite.invoicer.core.testing

import gr.edusuite.invoicer.core.createInvoiceUseCase
import gr.edusuite.invoicer.core.euros
import gr.edusuite.invoicer.core.lateCancel
import gr.edusuite.invoicer.core.lesson
import gr.edusuite.invoicer.core.model.AbsenceCharge
import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.InvoicePayment
import gr.edusuite.invoicer.core.report.Reports
import gr.edusuite.invoicer.core.report.UninvoicedSummary
import gr.edusuite.invoicer.core.student
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
        val taken = repos.saveLesson(lesson(maria))
        val first = Invoice(number = "2026-0001", studentId = maria, issueDate = issueDate, total = euros("20"))
        repos.createInvoice(first, listOf(taken), null)
        val second = first.copy(number = "2026-0002")

        val error = runCatching { repos.createInvoice(second, listOf(free, taken), null) }.exceptionOrNull()

        assertTrue("expected DomainException, got $error", error is DomainException)
        assertEquals(1, repos.invoices.size)
        assertNull(repos.getLesson(free)?.invoiceId)
    }

    @Test
    fun `absences can be linked like the SQL guard allows`() = runTest {
        val maria = repos.saveStudent(student())
        val excused = repos.saveLesson(lesson(maria, absent = true))
        val late = repos.saveLesson(lateCancel(maria))
        val invoice = Invoice(number = "2026-0001", studentId = maria, issueDate = issueDate, total = euros("20"))

        val id = repos.createInvoice(invoice, listOf(excused, late), paidOn = issueDate)

        assertEquals(listOf(id, id), repos.getLessons(listOf(excused, late)).map { it.invoiceId })
        assertNull("only charged lessons are marked paid", repos.getLesson(excused)?.paidOn)
        assertEquals(issueDate, repos.getLesson(late)?.paidOn)
    }

    @Test
    fun `setPaid leaves lessons that are not charged alone like the SQL update`() = runTest {
        val maria = repos.saveStudent(student())
        val excused = repos.saveLesson(lesson(maria, absent = true))
        val free = repos.saveLesson(lesson(maria, fee = "0"))
        val late = repos.saveLesson(lateCancel(maria))

        repos.setPaid(listOf(excused, free, late), issueDate)

        assertEquals(listOf(null, null, issueDate), repos.getLessons(listOf(excused, free, late)).map { it.paidOn })
    }

    @Test
    fun `aggregates match the rules in core`() = runTest {
        val maria = repos.saveStudent(student())
        val nikos = repos.saveStudent(student(firstName = "Nikos"))
        val a = repos.saveLesson(lesson(maria, fee = "20"))
        val b = repos.saveLesson(lesson(maria, fee = "30", paidOn = "2026-03-11"))
        val excused = repos.saveLesson(lesson(maria, fee = "20", absent = true))
        val late = repos.saveLesson(lateCancel(maria, fee = "25"))
        repos.saveLesson(lesson(nikos, fee = "50"))
        repos.saveLesson(lesson(nikos, fee = "15", absent = true))
        repos.saveLesson(lateCancel(nikos, fee = "12"))
        val invoice = repos.createInvoiceUseCase()(maria, listOf(a, b, excused, late), issueDate)
        val onlyExcused = repos.saveLesson(lesson(nikos, fee = "15", absent = true, date = "2026-03-20"))
        val zero = repos.createInvoiceUseCase()(nikos, listOf(onlyExcused), issueDate)

        assertEquals(Reports.balances(repos.lessons), repos.observeBalances().first())
        assertEquals(euros("62"), repos.observeBalances().first().first { it.studentId == nikos }.outstanding)
        assertEquals(
            mapOf(invoice.id to InvoicePayment(4, 2), zero.id to InvoicePayment(1, 0)),
            repos.observeInvoicePayments().first(),
        )
        assertTrue("a statement of excused absences is paid", repos.observeInvoicePayments().first()[zero.id]!!.isPaid)
    }

    @Test
    fun `home counts skip void and fully paid invoices`() = runTest {
        val maria = repos.saveStudent(student())
        val create = repos.createInvoiceUseCase()
        val open = repos.saveLesson(lesson(maria, fee = "20"))
        val paid = repos.saveLesson(lesson(maria, fee = "30", paidOn = "2026-03-11"))
        val voided = repos.saveLesson(lesson(maria, fee = "40"))
        repos.saveLesson(lesson(maria, fee = "50"))
        repos.saveLesson(lesson(maria, fee = "15", absent = true))
        val unpaidInvoice = create(maria, listOf(open), issueDate)
        val paidInvoice = create(maria, listOf(paid), issueDate)
        val voidInvoice = create(maria, listOf(voided), issueDate)
        repos.voidInvoice(voidInvoice.id, issueDate)

        assertEquals(5, repos.observeLessonCount().first())
        // The voided invoice released its lesson, and an absence is counted too.
        assertEquals(3, repos.observeUninvoicedCount().first())
        assertEquals(3, repos.observeInvoiceCount().first())
        assertEquals(1, repos.observeUnpaidCount().first())
        // The same answer as InvoicePayment.isPaid over observeInvoicePayments, for every invoice that is not void.
        val payments = repos.observeInvoicePayments().first()
        assertEquals(
            listOf(unpaidInvoice.id),
            repos.invoices.filter { !it.isVoid && payments[it.id]?.isPaid == false }.map { it.id },
        )
        assertTrue(payments[paidInvoice.id]!!.isPaid)

        repos.setPaid(listOf(open), issueDate)
        assertEquals(0, repos.observeUnpaidCount().first())

        // Only charged lessons can leave a statement unpaid: excused absences never do, late cancellations do.
        val excused = repos.saveLesson(lesson(maria, fee = "25", absent = true))
        create(maria, listOf(excused), issueDate)
        assertEquals(0, repos.observeUnpaidCount().first())
        val late = repos.saveLesson(lesson(maria, fee = "25", absent = true, absenceCharge = AbsenceCharge.LATE_CANCELLATION))
        create(maria, listOf(late), issueDate)
        assertEquals(1, repos.observeUnpaidCount().first())
    }

    @Test
    fun `the uninvoiced summary counts a period's open lessons and their students`() = runTest {
        val maria = repos.saveStudent(student())
        val nikos = repos.saveStudent(student(firstName = "Nikos"))
        val eleni = repos.saveStudent(student(firstName = "Eleni"))
        val billed = repos.saveLesson(lesson(maria, date = "2026-03-02"))
        repos.saveLesson(lesson(maria, date = "2026-03-01"))
        repos.saveLesson(lesson(maria, date = "2026-03-31"))
        repos.saveLesson(lesson(nikos, date = "2026-03-15", absent = true))
        repos.saveLesson(lesson(eleni, date = "2026-02-28"))
        repos.saveLesson(lesson(eleni, date = "2026-04-01"))
        repos.createInvoiceUseCase()(maria, listOf(billed), issueDate)
        val march = repos.observeUninvoicedSummary(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"))

        // Both ends are included, an absence counts, and the invoiced lesson and other months do not.
        assertEquals(UninvoicedSummary(lessons = 3, students = 2), march.first())
        assertEquals(
            UninvoicedSummary(0, 0),
            repos.observeUninvoicedSummary(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31")).first(),
        )
    }
    @Test fun `clearing payment repairs an uncharged legacy row`() = runTest {
        val maria = repos.saveStudent(student())
        val excused = repos.saveLesson(lesson(maria, absent = true, paidOn = "2026-03-31"))
        repos.setPaid(listOf(excused), null)
        assertNull(repos.getLesson(excused)?.paidOn)
    }

}
