package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.createInvoiceUseCase
import gr.edusuite.invoicer.core.euros
import gr.edusuite.invoicer.core.lateCancel
import gr.edusuite.invoicer.core.lesson
import gr.edusuite.invoicer.core.model.AbsenceCharge
import gr.edusuite.invoicer.core.student
import gr.edusuite.invoicer.core.testing.InMemoryRepositories
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
        repos.createInvoiceUseCase()(maria, listOf(id), LocalDate.parse("2026-03-31"))
        val invoiced = repos.getLesson(id)!!

        assertRejected("void the statement") { saveLesson(invoiced.copy(fee = euros("99"))) }
        assertRejected("void the statement") { saveLesson(invoiced.copy(absent = true)) }
        assertRejected("void the statement") { deleteLesson(id) }

        saveLesson(invoiced.copy(notes = "homework", paidOn = LocalDate.parse("2026-04-02"), invoiceId = null))
        val updated = repos.getLesson(id)!!
        assertEquals("homework", updated.notes)
        assertEquals(invoiced.invoiceId, updated.invoiceId)
        assertEquals(LocalDate.parse("2026-04-02"), updated.paidOn)
    }

    @Test
    fun `the absence charge is locked on a statement but its reason is not`() = runTest {
        val maria = repos.saveStudent(student())
        val id = repos.saveLesson(lesson(maria, absent = true, absenceNote = "ill"))
        repos.createInvoiceUseCase()(maria, listOf(id), LocalDate.parse("2026-03-31"))
        val invoiced = repos.getLesson(id)!!

        assertRejected("void the statement") {
            saveLesson(invoiced.copy(absenceCharge = AbsenceCharge.LATE_CANCELLATION))
        }
        assertRejected("void the statement") { saveLesson(invoiced.copy(absent = false)) }

        saveLesson(invoiced.copy(absenceNote = " flu "))
        assertEquals("flu", repos.getLesson(id)?.absenceNote)
        assertEquals(AbsenceCharge.NONE, repos.getLesson(id)?.absenceCharge)
    }

    @Test
    fun `excused absences are never paid`() = runTest {
        val maria = repos.saveStudent(student())
        val id = saveLesson(lesson(maria, paidOn = "2026-03-10"))

        saveLesson(repos.getLesson(id)!!.copy(absent = true))

        assertNull(repos.getLesson(id)?.paidOn)
        assertEquals(euros("0"), repos.getLesson(id)?.charge)
    }

    @Test
    fun `late cancellations are charged and keep their payment`() = runTest {
        val maria = repos.saveStudent(student())
        val id = saveLesson(lateCancel(maria, fee = "20", paidOn = "2026-03-10", note = " called at 9 "))

        val saved = repos.getLesson(id)!!
        assertEquals(LocalDate.parse("2026-03-10"), saved.paidOn)
        assertEquals(euros("20"), saved.charge)
        assertEquals("called at 9", saved.absenceNote)

        // Excusing it after all drops the payment, since nothing is charged any more.
        saveLesson(saved.copy(absenceCharge = AbsenceCharge.NONE))
        assertNull(repos.getLesson(id)?.paidOn)
    }

    @Test
    fun `a lesson that was held has no absence charge or reason`() = runTest {
        val maria = repos.saveStudent(student())
        val id = saveLesson(lateCancel(maria, note = "late"))

        saveLesson(repos.getLesson(id)!!.copy(absent = false))

        val saved = repos.getLesson(id)!!
        assertEquals(AbsenceCharge.NONE, saved.absenceCharge)
        assertEquals("", saved.absenceNote)
        assertEquals(
            "a new lesson that was held drops them too",
            AbsenceCharge.NONE,
            repos.getLesson(saveLesson(lateCancel(maria, note = "x").copy(absent = false)))?.absenceCharge,
        )
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
        repos.saveLesson(lateCancel(maria, fee = "12"))
        repos.saveLesson(lesson(nikos, fee = "25"))
        val today = LocalDate.parse("2026-03-31")

        val result = RecordPayment(repos)(maria, today)

        assertEquals(3, result.lessonCount)
        assertEquals(euros("62"), result.amount)
        val mariaLessons = repos.lessons.filter { it.studentId == maria }
        assertEquals(listOf(today, today, null, LocalDate.parse("2026-01-01"), today), mariaLessons.map { it.paidOn })
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
        repos.createInvoiceUseCase()(maria, listOf(invoiced), LocalDate.parse("2026-03-31"))
        val reschedule = RescheduleLessons(repos)
        val date = LocalDate.parse("2026-05-01")

        assertRejected("Paid") { reschedule(listOf(open, paid), date, LocalTime.NOON) }
        assertRejected("on a statement") { reschedule(listOf(open, invoiced), date, LocalTime.NOON) }
        assertRejected("no longer exist") { reschedule(listOf(open, 999), date, LocalTime.NOON) }
        assertRejected("at least one") { reschedule(emptyList(), date, LocalTime.NOON) }
        assertEquals(LocalDate.parse("2026-03-10"), repos.getLesson(open)?.date)
    }
    @Test fun `MarkPaid pays held and late lessons skips excused and counts distinct changes`() = runTest {
        val maria = repos.saveStudent(student())
        val held = repos.saveLesson(lesson(maria))
        val late = repos.saveLesson(lateCancel(maria))
        val excused = repos.saveLesson(lesson(maria, absent = true))
        val free = repos.saveLesson(lesson(maria, fee = "0"))
        val mark = MarkPaid(repos)
        val day = LocalDate.of(2026, 3, 31)
        assertEquals(2, mark(listOf(held, late, excused, free, held), day))
        assertEquals(day, repos.getLesson(held)?.paidOn)
        assertEquals(day, repos.getLesson(late)?.paidOn)
        assertNull(repos.getLesson(excused)?.paidOn)
        assertNull(repos.getLesson(free)?.paidOn)
        assertEquals(0, mark(listOf(held, late), day))
        assertEquals(0, mark(emptyList(), day))
        try {
            mark(listOf(excused), day)
            org.junit.Assert.fail("An excused absence cannot be paid")
        } catch (error: gr.edusuite.invoicer.core.model.DomainException) {
            assertEquals("An excused absence is not charged, so it can't be paid", error.message)
        }
    }

    @Test fun `MarkPaid clears any lesson and payment remains editable on a statement`() = runTest {
        val maria = repos.saveStudent(student())
        val held = repos.saveLesson(lesson(maria))
        repos.createInvoiceUseCase()(maria, listOf(held), LocalDate.of(2026, 3, 31))
        val day = LocalDate.of(2026, 4, 1)
        val mark = MarkPaid(repos)
        assertEquals(1, mark(listOf(held), day))
        org.junit.Assert.assertNotNull(repos.getLesson(held)?.invoiceId)
        // Legacy invalid payment: clearing must also repair an uncharged lesson.
        val excused = repos.saveLesson(lesson(maria, absent = true, paidOn = "2026-03-31"))
        assertEquals(2, mark(listOf(held, excused), null))
        assertNull(repos.getLesson(held)?.paidOn)
        assertNull(repos.getLesson(excused)?.paidOn)
    }

}
