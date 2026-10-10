package gr.eduinvoice.core.backup

import gr.eduinvoice.core.hourly
import gr.eduinvoice.core.lesson
import gr.eduinvoice.core.model.AppSettings
import gr.eduinvoice.core.model.BusinessProfile
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.model.StudentGroup
import gr.eduinvoice.core.model.ThemeMode
import gr.eduinvoice.core.perLesson
import gr.eduinvoice.core.student
import gr.eduinvoice.core.euros
import gr.eduinvoice.core.testing.InMemoryRepositories
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupCodecTest {
    private val snapshot = BackupSnapshot(
        exportedAt = "2026-10-10T09:00:00Z",
        settings = AppSettings(
            business = BusinessProfile(name = "Δημήτρης Τ.", address = "Οδός 1\nΑθήνα", iban = "GR16 0110"),
            currencyCode = "EUR",
            defaultLessonMinutes = 45,
            themeMode = ThemeMode.DARK,
            invoiceFooter = "Thank you \"very\" much \\ ☺",
        ),
        students = listOf(
            student(id = 1).copy(email = "m@example.com", notes = "tab\there", archived = true),
            student(firstName = "Nikos", id = 2, rate = perLesson("15")),
        ),
        groups = listOf(StudentGroup(id = 3, name = "B1", rate = hourly("12"), memberIds = setOf(2, 1))),
        lessons = listOf(
            lesson(studentId = 1, id = 4, paidOn = "2026-03-02").copy(invoiceId = 6),
            lesson(studentId = 2, id = 5, absent = true).copy(groupId = 3, notes = "\u0001 control"),
        ),
        invoices = listOf(
            Invoice(id = 6, number = "2026-0001", studentId = 1, issueDate = LocalDate.parse("2026-03-31"), total = euros("20")),
            Invoice(
                id = 7,
                number = "2026-0002",
                studentId = 2,
                issueDate = LocalDate.parse("2026-04-01"),
                total = euros("15"),
                voidedOn = LocalDate.parse("2026-04-03"),
            ),
        ),
    )

    @Test
    fun `round trips every field`() {
        assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
    }

    @Test
    fun `rejects other files and future versions`() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"hello": 1}""") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("[1, 2]") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
        val future = BackupCodec.encode(snapshot).replace("\"version\": 2", "\"version\": 3")
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(future) }
        assertTrue(error.message!!.contains("newer"))
    }

    @Test
    fun `reads version 1 backups written before invoices could be voided`() {
        val v1 = BackupCodec.encode(snapshot)
            .replace("\"version\": 2", "\"version\": 1")
            .replace(Regex(",\\s*\"voidedOn\": (null|\"[0-9-]+\")"), "")
        assertTrue("fixture must not mention voidedOn", "voidedOn" !in v1)

        val decoded = BackupCodec.decode(v1)

        assertEquals(snapshot.invoices.map { it.copy(voidedOn = null) }, decoded.invoices)
        assertEquals(snapshot.lessons, decoded.lessons)
    }

    @Test
    fun `reports the missing field`() {
        val broken = BackupCodec.encode(snapshot).replace("\"feeCents\"", "\"fee\"")
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(broken) }
        assertEquals("Missing field 'feeCents'", error.message)
    }

    @Test
    fun `in-memory repository restores a snapshot`() = runTest {
        val repos = InMemoryRepositories()
        repos.replaceAll(snapshot)
        val restored = repos.readAll()
        assertEquals(snapshot.copy(exportedAt = restored.exportedAt), restored)
        assertEquals(8L, repos.saveStudent(student()))
    }
}
