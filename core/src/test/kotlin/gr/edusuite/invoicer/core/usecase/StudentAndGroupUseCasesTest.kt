package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.createInvoiceUseCase
import gr.edusuite.invoicer.core.euros
import gr.edusuite.invoicer.core.hourly
import gr.edusuite.invoicer.core.lesson
import gr.edusuite.invoicer.core.model.AbsenceCharge
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.perLesson
import gr.edusuite.invoicer.core.student
import gr.edusuite.invoicer.core.testing.InMemoryRepositories
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StudentAndGroupUseCasesTest {
    private val repos = InMemoryRepositories()

    @Test
    fun `save student trims fields and validates`() = runTest {
        val save = SaveStudent(repos)
        val id = save(student().copy(firstName = " Maria ", email = " maria@example.com "))
        assertEquals("Maria", repos.getStudent(id)?.firstName)
        assertEquals("maria@example.com", repos.getStudent(id)?.email)

        assertRejected("First name") { save(student().copy(firstName = " ")) }
        assertRejected("Email") { save(student().copy(email = "maria@")) }
    }

    @Test
    fun `students with lessons cannot be deleted`() = runTest {
        val delete = DeleteStudent(repos, repos, repos)
        val maria = repos.saveStudent(student())
        val nikos = repos.saveStudent(student(firstName = "Nikos"))
        repos.saveLesson(lesson(maria))

        assertRejected("Archive") { delete(maria) }
        delete(nikos)
        assertNull(repos.getStudent(nikos))
    }

    @Test
    fun `students with invoices cannot be deleted, even once their lessons are gone`() = runTest {
        val maria = repos.saveStudent(student())
        val lessonId = repos.saveLesson(lesson(maria))
        val invoice = repos.createInvoiceUseCase()(maria, listOf(lessonId), LocalDate.parse("2026-03-31"))
        VoidInvoice(repos)(invoice.id, LocalDate.parse("2026-04-01"))
        DeleteLesson(repos)(lessonId)

        assertRejected("statements") { DeleteStudent(repos, repos, repos)(maria) }
        assertEquals("Maria", repos.getStudent(maria)?.firstName)
    }

    @Test
    fun `groups with invoiced lessons can only be archived`() = runTest {
        val maria = repos.saveStudent(student())
        val invoicedGroup = repos.saveGroup(StudentGroup(name = "A", rate = hourly("12"), memberIds = setOf(maria)))
        val plainGroup = repos.saveGroup(StudentGroup(name = "B", rate = hourly("12"), memberIds = setOf(maria)))
        val invoicedLesson = repos.saveLesson(lesson(maria).copy(groupId = invoicedGroup))
        val plainLesson = repos.saveLesson(lesson(maria).copy(groupId = plainGroup))
        repos.createInvoiceUseCase()(maria, listOf(invoicedLesson), LocalDate.parse("2026-03-31"))
        val delete = DeleteGroup(repos, repos)

        assertRejected("Archive") { delete(invoicedGroup) }
        assertEquals(invoicedGroup, repos.getLesson(invoicedLesson)?.groupId)

        delete(plainGroup)
        assertNull(repos.getGroup(plainGroup))
        assertNull("lessons are kept, without the group", repos.getLesson(plainLesson)?.groupId)
    }

    @Test
    fun `group lesson bills attendees at the group rate and records absences`() = runTest {
        val maria = repos.saveStudent(student(rate = hourly("30")))
        val nikos = repos.saveStudent(student(firstName = "Nikos"))
        val eleni = repos.saveStudent(student(firstName = "Eleni"))
        val kostas = repos.saveStudent(student(firstName = "Kostas"))
        val groupId = SaveGroup(repos)(
            StudentGroup(name = " B1 English ", rate = hourly("12"), memberIds = setOf(maria, nikos, eleni, kostas)),
        )

        val ids = LogGroupLesson(repos, repos)(
            groupId = groupId,
            date = LocalDate.parse("2026-03-10"),
            startTime = LocalTime.of(18, 0),
            durationMinutes = 90,
            attendance = listOf(
                // A present member's charge and reason are ignored.
                Attendance(maria, charge = AbsenceCharge.LATE_CANCELLATION, note = "ignored"),
                Attendance(nikos, absent = true, note = " ill "),
                Attendance(eleni, absent = true, charge = AbsenceCharge.LATE_CANCELLATION, note = "cancelled at 17:00"),
            ),
        )

        assertEquals("B1 English", repos.getGroup(groupId)?.name)
        assertEquals(3, ids.size)
        val rows = repos.getLessons(ids).associateBy { it.studentId }
        val present = rows.getValue(maria)
        assertEquals(euros("18"), present.fee)
        assertEquals(false, present.absent)
        assertEquals(AbsenceCharge.NONE, present.absenceCharge)
        assertEquals("", present.absenceNote)

        val excused = rows.getValue(nikos)
        assertEquals(true, excused.absent)
        assertEquals(euros("0"), excused.charge)
        assertEquals("ill", excused.absenceNote)

        val late = rows.getValue(eleni)
        assertEquals(true, late.absent)
        assertEquals(AbsenceCharge.LATE_CANCELLATION, late.absenceCharge)
        assertEquals(euros("18"), late.charge)
        assertEquals("cancelled at 17:00", late.absenceNote)

        assertEquals(setOf(groupId), rows.values.map { it.groupId }.toSet())
        assertEquals("members left out get no row", null, rows[kostas])
    }

    @Test
    fun `group lesson rejects strangers and empty sessions`() = runTest {
        val maria = repos.saveStudent(student())
        val outsider = repos.saveStudent(student(firstName = "Outsider"))
        val groupId = repos.saveGroup(StudentGroup(name = "G", rate = perLesson("10"), memberIds = setOf(maria)))
        val log = LogGroupLesson(repos, repos)
        val date = LocalDate.parse("2026-03-10")
        val time = LocalTime.NOON

        assertRejected("at least one") { log(groupId, date, time, 60, emptyList()) }
        assertRejected("not members") { log(groupId, date, time, 60, listOf(Attendance(maria), Attendance(outsider))) }
        assertRejected("only once") {
            log(groupId, date, time, 60, listOf(Attendance(maria), Attendance(maria, absent = true)))
        }
        assertRejected("not found") { log(999, date, time, 60, listOf(Attendance(maria))) }
        assertEquals("nothing was logged", 0, repos.lessons.size)
    }
}
