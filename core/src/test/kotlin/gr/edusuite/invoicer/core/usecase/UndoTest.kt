package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.*
import gr.edusuite.invoicer.core.model.*
import gr.edusuite.invoicer.core.testing.InMemoryRepositories
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class UndoTest {
    private val repos = InMemoryRepositories()
    private val day = LocalDate.parse("2026-03-10")

    @Test fun `delete undo restores every field and same id only once`() = runTest {
        val student = repos.saveStudent(student())
        val group = repos.saveGroup(StudentGroup(name = "Class", rate = hourly("20"), memberIds = setOf(student)))
        val id = repos.saveLesson(lateCancel(student, note = "Ill", paidOn = "2026-03-01").copy(groupId = group, notes = "Keep me"))
        val before = repos.getLesson(id)
        val action = DeleteLesson(repos)(id)
        assertNull(repos.getLesson(id))
        action.undo()
        action.undo()
        assertEquals(before, repos.getLesson(id))
        repos.createInvoiceUseCase()(student, listOf(id), day)
        assertTrue(runCatching { DeleteLesson(repos)(id) }.exceptionOrNull() is DomainException)
    }

    @Test fun `delete undo explains a missing student`() = runTest {
        val student = repos.saveStudent(student())
        val id = repos.saveLesson(lesson(student))
        val action = DeleteLesson(repos)(id)
        repos.deleteStudent(student)
        val error = runCatching { action.undo() }.exceptionOrNull()
        assertTrue(error is DomainException)
        assertTrue(error!!.message!!.contains("student"))
        assertNull(repos.getLesson(id))
    }

    @Test fun `record payment undo restores snapshot and leaves other lessons alone`() = runTest {
        val student = repos.saveStudent(student())
        val other = repos.saveStudent(student("Other"))
        listOf(lesson(student), lateCancel(student), lesson(student, absent = true),
            lesson(student, paidOn = "2026-03-01"), lesson(other)).forEach { repos.saveLesson(it) }
        val before = repos.lessons.toList()
        val action = RecordPayment(repos)(student, day)
        assertEquals(PaymentResult(2, euros("40")), action.result)
        action.undo()
        action.undo()
        assertEquals(before, repos.lessons.toList())
    }

    @Test fun `mark paid and unpaid undo restore each previous date and every other field`() = runTest {
        val student = repos.saveStudent(student())
        val a = repos.saveLesson(lesson(student, paidOn = "2026-03-01"))
        val b = repos.saveLesson(lesson(student))
        val before = repos.lessons.toList()
        val action = MarkPaid(repos)(listOf(a, b), day)
        assertEquals(2, action.result)
        action.undo()
        action.undo()
        assertEquals(before, repos.lessons.toList())
        val unpaid = MarkPaid(repos)(listOf(a, b), null)
        unpaid.undo()
        unpaid.undo()
        assertEquals(before, repos.lessons.toList())
    }

    @Test fun `reschedule undo restores dates and times but keeps later notes`() = runTest {
        val student = repos.saveStudent(student())
        val ids = listOf(repos.saveLesson(lesson(student)), repos.saveLesson(lesson(student, date = "2026-03-11")))
        val before = repos.lessons.toList()
        val action = RescheduleLessons(repos)(ids, day.plusDays(10), LocalTime.NOON)
        assertEquals(2, action.result)
        action.undo()
        action.undo()
        assertEquals(before, repos.lessons.toList())
        val second = RescheduleLessons(repos)(ids, day.plusDays(10), LocalTime.NOON)
        val edited = repos.getLesson(ids.first())!!.copy(notes = "Later edit")
        repos.saveLesson(edited)
        second.undo()
        assertEquals(before.first().copy(notes = "Later edit"), repos.getLesson(ids.first()))
    }

    @Test fun `reschedule undo checks every row before writing when a statement locks one`() = runTest {
        val student = repos.saveStudent(student())
        val ids = listOf(repos.saveLesson(lesson(student)), repos.saveLesson(lesson(student)))
        val action = RescheduleLessons(repos)(ids, day.plusDays(10), LocalTime.NOON)
        repos.createInvoiceUseCase()(student, listOf(ids.last()), day)
        val beforeUndo = repos.lessons.toList()
        assertEquals("A lesson is now on a statement; it can't be moved back", runCatching { action.undo() }.exceptionOrNull()?.message)
        assertEquals(beforeUndo, repos.lessons.toList())
    }

    @Test fun `reschedule undo refuses a later payment without changing any row`() = runTest {
        val student = repos.saveStudent(student())
        val id = repos.saveLesson(lesson(student))
        val action = RescheduleLessons(repos)(listOf(id), day.plusDays(10), LocalTime.NOON)
        MarkPaid(repos)(listOf(id), day)
        val beforeUndo = repos.lessons
        assertEquals("A lesson is now paid; it can't be moved back", runCatching { action.undo() }.exceptionOrNull()?.message)
        assertEquals(beforeUndo, repos.lessons)
        MarkPaid(repos)(listOf(id), null)
        action.undo()
        assertEquals(day, repos.getLesson(id)?.date)
    }

    @Test fun `archive and restore student and group undo are idempotent and keep later edits`() = runTest {
        val student = repos.saveStudent(student())
        val group = repos.saveGroup(StudentGroup(name = "Class", rate = hourly("20"), memberIds = setOf(student)))
        for (archived in listOf(true, false)) {
            repos.saveStudent(repos.getStudent(student)!!.copy(archived = !archived))
            repos.saveGroup(repos.getGroup(group)!!.copy(archived = !archived))
            val beforeStudent = repos.getStudent(student)
            val beforeGroup = repos.getGroup(group)
            val a = ArchiveStudent(repos)(student, archived)
            val b = ArchiveGroup(repos)(group, archived)
            a.undo(); b.undo(); a.undo(); b.undo()
            assertEquals(beforeStudent, repos.getStudent(student))
            assertEquals(beforeGroup, repos.getGroup(group))
        }
        val action = ArchiveStudent(repos)(student, true)
        repos.saveStudent(repos.getStudent(student)!!.copy(notes = "Later edit"))
        action.undo()
        assertEquals("Later edit", repos.getStudent(student)!!.notes)
        val noChange = ArchiveGroup(repos)(group, repos.getGroup(group)!!.archived)
        val before = repos.getGroup(group)
        noChange.undo()
        assertEquals(before, repos.getGroup(group))
    }
}
