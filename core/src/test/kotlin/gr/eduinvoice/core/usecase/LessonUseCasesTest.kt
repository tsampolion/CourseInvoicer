package gr.eduinvoice.core.usecase

import gr.eduinvoice.core.euros
import gr.eduinvoice.core.lesson
import gr.eduinvoice.core.student
import gr.eduinvoice.core.testing.InMemoryRepositories
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LessonUseCasesTest {
    private val repos = InMemoryRepositories()
    private val saveLesson = SaveLesson(repos)
    private val deleteLesson = DeleteLesson(repos)

    @Test
    fun `validates duration and fee`() = runTest {
        val maria = repos.saveStudent(student())
        assertRejected("Duration") { saveLesson(lesson(maria, minutes = 0)) }
        assertRejected("Duration") { saveLesson(lesson(maria, minutes = MAX_LESSON_MINUTES + 1)) }
        assertRejected("negative") { saveLesson(lesson(maria).copy(fee = euros("1") - euros("2"))) }
    }

    @Test
    fun `new lessons never start on an invoice`() = runTest {
        val maria = repos.saveStudent(student())
        val id = saveLesson(lesson(maria).copy(invoiceId = 42, notes = "  algebra "))
        val saved = repos.getLesson(id)!!
        assertNull(saved.invoiceId)
        assertEquals("algebra", saved.notes)
    }

    @Test
    fun `invoiced lessons keep their billing fields but allow notes and payment`() = runTest {
        val maria = repos.saveStudent(student())
        val id = repos.saveLesson(lesson(maria))
        CreateInvoice(repos, repos)(maria, listOf(id), LocalDate.parse("2026-03-31"))
        val invoiced = repos.getLesson(id)!!

        assertRejected("delete the invoice") { saveLesson(invoiced.copy(fee = euros("99"))) }
        assertRejected("delete the invoice") { saveLesson(invoiced.copy(absent = true)) }
        assertRejected("delete the invoice") { deleteLesson(id) }

        saveLesson(invoiced.copy(notes = "homework", paidOn = LocalDate.parse("2026-04-02"), invoiceId = null))
        val updated = repos.getLesson(id)!!
        assertEquals("homework", updated.notes)
        assertEquals(invoiced.invoiceId, updated.invoiceId)
        assertEquals(LocalDate.parse("2026-04-02"), updated.paidOn)
    }

    @Test
    fun `deletes lessons that are not invoiced`() = runTest {
        val maria = repos.saveStudent(student())
        val id = repos.saveLesson(lesson(maria))
        deleteLesson(id)
        assertNull(repos.getLesson(id))
    }

    @Test
    fun `record payment settles every outstanding lesson of the student`() = runTest {
        val maria = repos.saveStudent(student())
        val nikos = repos.saveStudent(student(firstName = "Nikos"))
        repos.saveLesson(lesson(maria, fee = "20"))
        repos.saveLesson(lesson(maria, fee = "30"))
        repos.saveLesson(lesson(maria, fee = "15", absent = true))
        repos.saveLesson(lesson(maria, fee = "10", paidOn = "2026-01-01"))
        repos.saveLesson(lesson(nikos, fee = "25"))
        val today = LocalDate.parse("2026-03-31")

        val result = RecordPayment(repos)(maria, today)

        assertEquals(2, result.lessonCount)
        assertEquals(euros("50"), result.amount)
        val mariaLessons = repos.lessons.filter { it.studentId == maria }
        assertEquals(listOf(today, today, null, LocalDate.parse("2026-01-01")), mariaLessons.map { it.paidOn })
        assertNull(repos.lessons.single { it.studentId == nikos }.paidOn)
    }

    @Test
    fun `reschedule moves open lessons and keeps their fee and length`() = runTest {
        val maria = repos.saveStudent(student())
        val nikos = repos.saveStudent(student(firstName = "Nikos"))
        val a = repos.saveLesson(lesson(maria, date = "2026-03-10", fee = "20"))
        val b = repos.saveLesson(lesson(nikos, date = "2026-03-10", fee = "15", minutes = 90))
        val untouched = repos.saveLesson(lesson(maria, date = "2026-03-11"))
        val newDate = LocalDate.parse("2026-03-17")

        val moved = RescheduleLessons(repos)(listOf(a, b), newDate, LocalTime.of(18, 30))

        assertEquals(2, moved)
        repos.getLessons(listOf(a, b)).forEach {
            assertEquals(newDate, it.date)
            assertEquals(LocalTime.of(18, 30), it.startTime)
        }
        assertEquals(euros("15"), repos.getLesson(b)?.fee)
        assertEquals(90, repos.getLesson(b)?.durationMinutes)
        assertEquals(LocalDate.parse("2026-03-11"), repos.getLesson(untouched)?.date)
    }

    @Test
    fun `reschedule refuses paid, invoiced or missing lessons and changes nothing`() = runTest {
        val maria = repos.saveStudent(student())
        val open = repos.saveLesson(lesson(maria))
        val paid = repos.saveLesson(lesson(maria, paidOn = "2026-03-10"))
        val invoiced = repos.saveLesson(lesson(maria))
        CreateInvoice(repos, repos)(maria, listOf(invoiced), LocalDate.parse("2026-03-31"))
        val reschedule = RescheduleLessons(repos)
        val date = LocalDate.parse("2026-05-01")

        assertRejected("Paid") { reschedule(listOf(open, paid), date, LocalTime.NOON) }
        assertRejected("Invoiced") { reschedule(listOf(open, invoiced), date, LocalTime.NOON) }
        assertRejected("no longer exist") { reschedule(listOf(open, 999), date, LocalTime.NOON) }
        assertRejected("at least one") { reschedule(emptyList(), date, LocalTime.NOON) }
        assertEquals(LocalDate.parse("2026-03-10"), repos.getLesson(open)?.date)
    }
}
