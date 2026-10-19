package gr.edusuite.invoicer.core.privacy

import gr.edusuite.invoicer.core.backup.Json
import gr.edusuite.invoicer.core.createInvoiceUseCase
import gr.edusuite.invoicer.core.lesson
import gr.edusuite.invoicer.core.student
import gr.edusuite.invoicer.core.testing.InMemoryRepositories
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StudentDataExportTest {
    private val repos = InMemoryRepositories()

    @Test fun exportHoldsOnlyThatStudent() = runTest {
        val maria = repos.saveStudent(student().copy(guardianName = "Eleni"))
        val nikos = repos.saveStudent(student(firstName = "Nikos"))
        val first = repos.saveLesson(lesson(maria, date = "2026-03-02"))
        repos.saveLesson(lesson(maria, date = "2026-03-09", fee = "25"))
        repos.saveLesson(lesson(nikos))
        repos.createInvoiceUseCase()(maria, listOf(first), LocalDate.parse("2026-03-31"))
        repos.createInvoiceUseCase()(nikos, repos.lessons.filter { it.studentId == nikos }.map { it.id }, LocalDate.parse("2026-03-31"))

        val text = studentDataJson(repos.getStudent(maria)!!, repos.lessons, repos.invoices, "2026-10-18T12:00:00Z")

        val root = Json.parse(text) as Map<*, *>
        assertEquals(STUDENT_DATA_FORMAT, root["format"])
        assertEquals(1L, root["version"])
        val student = root["student"] as Map<*, *>
        assertEquals("Maria", student["firstName"])
        assertEquals("Eleni", student["guardianName"])
        val lessons = root["lessons"] as List<*>
        assertEquals(2, lessons.size)
        assertEquals(listOf(maria, maria), lessons.map { (it as Map<*, *>)["studentId"] })
        assertEquals(2500L, lessons.map { (it as Map<*, *>)["feeCents"] }.last())
        val statements = root["statements"] as List<*>
        assertEquals(1, statements.size)
        assertEquals(maria, (statements.single() as Map<*, *>)["studentId"])
        assertEquals("no other student's id", false, text.contains("\"studentId\": $nikos,") || text.contains("\"studentId\":$nikos,"))
    }
}
