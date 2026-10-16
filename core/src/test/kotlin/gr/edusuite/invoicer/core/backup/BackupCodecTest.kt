package gr.edusuite.invoicer.core.backup

import gr.edusuite.invoicer.core.hourly
import gr.edusuite.invoicer.core.lesson
import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.BusinessProfile
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.InvoiceDocument
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.model.ThemeMode
import gr.edusuite.invoicer.core.perLesson
import gr.edusuite.invoicer.core.student
import gr.edusuite.invoicer.core.euros
import gr.edusuite.invoicer.core.testing.InMemoryRepositories
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
            Invoice(
                id = 6,
                number = "2026-0001",
                studentId = 1,
                issueDate = LocalDate.parse("2026-03-31"),
                total = euros("20"),
                document = InvoiceDocument(
                    number = "2026-0001",
                    issueDate = "31 Mar 2026",
                    seller = listOf("Δημήτρης Τ.", "Οδός 1"),
                    billTo = listOf("Maria Papadopoulou"),
                    lines = listOf(InvoiceDocument.Line("2 Mar 2026", "Lesson — \"quoted\"", "1 h", "€20.00")),
                    total = "€20.00",
                    paid = false,
                    notes = emptyList(),
                ),
            ),
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
        val future = BackupCodec.encode(snapshot).replace("\"version\": 3", "\"version\": 4")
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(future) }
        assertTrue(error.message!!.contains("newer"))
    }

    @Test
    fun `writes the Course Invoicer format and still reads EduInvoice backups`() {
        val current = BackupCodec.encode(snapshot)
        assertTrue(current.contains("\"format\": \"course-invoicer-backup\""))

        val legacy = current.replace("course-invoicer-backup", "eduinvoice-backup")
        assertEquals(snapshot, BackupCodec.decode(legacy))

        val legacyV2 = BackupCodec.encode(snapshot.withoutDocuments())
            .replace("course-invoicer-backup", "eduinvoice-backup")
            .replace("\"version\": 3", "\"version\": 2")
            .replace(Regex(",\\s*\"document\": null"), "")
        assertEquals(snapshot.withoutDocuments(), BackupCodec.decode(legacyV2))

        val other = current.replace("course-invoicer-backup", "some-other-backup")
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(other) }
        assertEquals("Not a Course Invoicer backup", error.message)
    }

    @Test
    fun `ignores the removed Tax ID in older backups`() {
        val current = BackupCodec.encode(snapshot)
        assertTrue("taxId is no longer written", "taxId" !in current)

        val withTaxId = current.replace("\"businessName\":", "\"taxId\": \"123\", \"businessName\":")
        assertTrue("fixture must contain taxId", "\"taxId\": \"123\"" in withTaxId)
        assertEquals(snapshot, BackupCodec.decode(withTaxId))
    }

    @Test
    fun `reads version 1 backups written before invoices could be voided`() {
        val v1 = BackupCodec.encode(snapshot.withoutDocuments())
            .replace("\"version\": 3", "\"version\": 1")
            .replace(Regex(",\\s*\"voidedOn\": (null|\"[0-9-]+\")"), "")
            .replace(Regex(",\\s*\"document\": null"), "")
        assertTrue("fixture must not mention voidedOn", "voidedOn" !in v1)
        assertTrue("fixture must not mention document", "document" !in v1)
        // A void invoice holds no lessons, so a v1 file with every invoice open is still consistent.
        val decoded = BackupCodec.decode(v1)

        assertEquals(snapshot.withoutDocuments().invoices.map { it.copy(voidedOn = null) }, decoded.invoices)
        assertEquals(snapshot.lessons, decoded.lessons)
    }

    @Test
    fun `reads version 2 backups written before documents were stored`() {
        val v2 = BackupCodec.encode(snapshot.withoutDocuments())
            .replace("\"version\": 3", "\"version\": 2")
            .replace(Regex(",\\s*\"document\": null"), "")
        assertTrue("fixture must not mention document", "document" !in v2)

        assertEquals(snapshot.withoutDocuments(), BackupCodec.decode(v2))
    }

    @Test
    fun `rejects files that break the app's rules and names the problem`() {
        fun rejects(broken: BackupSnapshot, messagePart: String) {
            val text = BackupCodec.encode(broken)
            val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(text) }
            assertTrue("'${error.message}' should mention '$messagePart'", error.message!!.contains(messagePart))
        }
        val lessonOnInvoice = snapshot.lessons.first()
        fun withLesson(extra: Lesson) = snapshot.copy(lessons = snapshot.lessons + extra)

        rejects(snapshot.copy(settings = snapshot.settings.copy(currencyCode = "XX")), "currency")
        rejects(snapshot.copy(settings = snapshot.settings.copy(defaultLessonMinutes = 0)), "lesson length")
        rejects(snapshot.copy(students = snapshot.students + snapshot.students.first()), "share an id")
        rejects(withLesson(lessonOnInvoice.copy(id = 9, durationMinutes = 900)), "Lesson 9")
        rejects(withLesson(lessonOnInvoice.copy(id = 9, studentId = 99)), "Lesson 9")
        rejects(withLesson(lessonOnInvoice.copy(id = 9, invoiceId = 7)), "invoice 2026-0002")
        rejects(withLesson(lessonOnInvoice.copy(id = 9, invoiceId = 70)), "missing invoice")
        rejects(snapshot.copy(groups = snapshot.groups.map { it.copy(memberIds = it.memberIds + 99) }), "Group 3")
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

    private fun BackupSnapshot.withoutDocuments() = copy(invoices = invoices.map { it.copy(document = null) })
}
