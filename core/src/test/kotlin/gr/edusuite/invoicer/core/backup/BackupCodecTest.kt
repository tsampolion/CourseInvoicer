package gr.edusuite.invoicer.core.backup

import gr.edusuite.invoicer.core.euros
import gr.edusuite.invoicer.core.hourly
import gr.edusuite.invoicer.core.invoice.InvoiceDocumentBuilder
import gr.edusuite.invoicer.core.lateCancel
import gr.edusuite.invoicer.core.lesson
import gr.edusuite.invoicer.core.model.AbsenceCharge
import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.BusinessProfile
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.InvoiceDocument
import gr.edusuite.invoicer.core.model.MAX_RATE
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.ReminderSettings
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.model.ThemeMode
import gr.edusuite.invoicer.core.perLesson
import gr.edusuite.invoicer.core.student
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
            defaultLessonMinutes = 45,
            themeMode = ThemeMode.DARK,
            invoiceFooter = "Thank you \"very\" much \\ ☺",
            showAddressOnStatements = true,
        ),
        students = listOf(
            student(id = 1).copy(email = "m@example.com", notes = "tab\there", archived = true),
            student(firstName = "Nikos", id = 2, rate = perLesson("15")),
        ),
        groups = listOf(StudentGroup(id = 3, name = "B1", rate = hourly("12"), memberIds = setOf(2, 1))),
        lessons = listOf(
            lesson(studentId = 1, id = 4, paidOn = "2026-03-02").copy(invoiceId = 6),
            lesson(studentId = 2, id = 5, absent = true).copy(groupId = 3, notes = "\u0001 control"),
            // Version 4: an excused absence on a statement, and a paid late cancellation with a reason.
            lesson(studentId = 1, id = 8, absent = true, absenceNote = "ill").copy(invoiceId = 6),
            lateCancel(studentId = 1, id = 9, paidOn = "2026-03-20", note = "cancelled at 16:30 \"late\""),
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
                    lines = listOf(
                        InvoiceDocument.Line("2 Mar 2026", "Lesson — \"quoted\"", "1 h", "€20.00"),
                        InvoiceDocument.Line("9 Mar 2026", "Lesson", "1 h", "€0.00", "Absent — not charged: ill"),
                    ),
                    total = "€20.00",
                    paid = false,
                    notes = emptyList(),
                    title = InvoiceDocumentBuilder.TITLE,
                    disclaimer = InvoiceDocumentBuilder.DISCLAIMER,
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

    /**
     * What an app writing version 3 could have stored: no late cancellations, absence reasons, absences on
     * statements, address switch or statement wording in documents.
     */
    private val v3Snapshot = snapshot.copy(
        settings = snapshot.settings.copy(showAddressOnStatements = false),
        lessons = snapshot.lessons.filter { it.id in setOf(4L, 5L) },
        invoices = snapshot.invoices.map { invoice ->
            invoice.copy(
                document = invoice.document?.let { doc ->
                    doc.copy(title = null, disclaimer = null, lines = doc.lines.take(1))
                },
            )
        },
    )

    @Test fun `writes EUR for old readers and ignores any legacy currency without changing data`() {
        val encoded = BackupCodec.encode(snapshot)
        assertTrue(encoded.contains("\"currencyCode\": \"EUR\""))
        for (legacy in listOf("GBP", "JPY", "KWD", "unknown")) {
            assertEquals(snapshot, BackupCodec.decode(encoded.replace("\"EUR\"", "\"$legacy\"")))
        }
    }

    @Test fun `restore accepts capped rates and rejects student and group rates above the cap`() {
        val capped = snapshot.copy(
            students = snapshot.students.map { it.copy(rate = it.rate.copy(amount = MAX_RATE)) },
            groups = snapshot.groups.map { it.copy(rate = it.rate.copy(amount = MAX_RATE)) },
        )
        assertEquals(capped, BackupCodec.decode(BackupCodec.encode(capped)))
        val brokenStudent = capped.copy(students = capped.students.map { it.copy(rate = it.rate.copy(amount = MAX_RATE + Money(1))) })
        val brokenGroup = capped.copy(groups = capped.groups.map { it.copy(rate = it.rate.copy(amount = MAX_RATE + Money(1))) })
        for ((broken, message) in listOf(brokenStudent to "Student 1", brokenGroup to "Group 3")) {
            val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(BackupCodec.encode(broken)) }
            assertTrue(error.message!!.contains(message))
            assertTrue(error.message!!.contains("100,000"))
        }
    }

    @Test
    fun `round trips every field`() {
        assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
    }

    @Test
    fun `rejects other files and future versions`() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"hello": 1}""") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("[1, 2]") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
        val current = BackupCodec.encode(snapshot)
        assertTrue(current.contains("\"version\": 4"))
        val future = current.replace("\"version\": 4", "\"version\": 5")
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(future) }
        assertTrue(error.message!!.contains("newer"))
    }

    @Test
    fun `writes the Course Invoicer format and still reads EduInvoice backups`() {
        val current = BackupCodec.encode(snapshot)
        assertTrue(current.contains("\"format\": \"course-invoicer-backup\""))

        val legacyV3 = v3Snapshot.encodedAs(version = 3).replace("course-invoicer-backup", "eduinvoice-backup")
        assertEquals(v3Snapshot, BackupCodec.decode(legacyV3))

        val legacyV2 = v3Snapshot.withoutDocuments().encodedAs(version = 2)
            .replace("course-invoicer-backup", "eduinvoice-backup")
            .replace(Regex(",\\s*\"document\": null"), "")
        assertEquals(v3Snapshot.withoutDocuments(), BackupCodec.decode(legacyV2))

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
        val v1 = v3Snapshot.withoutDocuments().encodedAs(version = 1)
            .replace(Regex(",\\s*\"voidedOn\": (null|\"[0-9-]+\")"), "")
            .replace(Regex(",\\s*\"document\": null"), "")
        assertTrue("fixture must not mention voidedOn", "voidedOn" !in v1)
        assertTrue("fixture must not mention document", "document" !in v1)
        // A void invoice holds no lessons, so a v1 file with every invoice open is still consistent.
        val decoded = BackupCodec.decode(v1)

        assertEquals(v3Snapshot.withoutDocuments().invoices.map { it.copy(voidedOn = null) }, decoded.invoices)
        assertEquals(v3Snapshot.lessons, decoded.lessons)
    }

    @Test
    fun `reads version 2 backups written before documents were stored`() {
        val v2 = v3Snapshot.withoutDocuments().encodedAs(version = 2)
            .replace(Regex(",\\s*\"document\": null"), "")
        assertTrue("fixture must not mention document", "document" !in v2)

        assertEquals(v3Snapshot.withoutDocuments(), BackupCodec.decode(v2))
    }

    @Test
    fun `reads version 3 backups written before absences could be charged`() {
        val v3 = v3Snapshot.encodedAs(version = 3)
        assertTrue("fixture must not mention absenceCharge", "absenceCharge" !in v3)
        assertTrue("fixture must not mention absenceNote", "absenceNote" !in v3)
        assertTrue("fixture must not mention the address switch", "showAddressOnStatements" !in v3)
        assertTrue("fixture must hold a document without the statement keys", "\"title\"" !in v3)

        val decoded = BackupCodec.decode(v3)

        assertEquals(v3Snapshot, decoded)
        assertTrue(decoded.lessons.all { it.absenceCharge == AbsenceCharge.NONE && it.absenceNote.isEmpty() })
        assertEquals(false, decoded.settings.showAddressOnStatements)
        assertEquals(null, decoded.invoices.first().document?.title)
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

        rejects(snapshot.copy(settings = snapshot.settings.copy(defaultLessonMinutes = 0)), "lesson length")
        rejects(snapshot.copy(students = snapshot.students + snapshot.students.first()), "share an id")
        rejects(withLesson(lessonOnInvoice.copy(id = 20, durationMinutes = 900)), "Lesson 20")
        rejects(withLesson(lessonOnInvoice.copy(id = 20, studentId = 99)), "Lesson 20")
        rejects(withLesson(lessonOnInvoice.copy(id = 20, invoiceId = 7)), "statement 2026-0002")
        rejects(withLesson(lessonOnInvoice.copy(id = 20, invoiceId = 70)), "missing statement")
        rejects(withLesson(lesson(studentId = 1, id = 20, absent = true, paidOn = "2026-03-02")), "cannot be paid")
        rejects(snapshot.copy(groups = snapshot.groups.map { it.copy(memberIds = it.memberIds + 99) }), "Group 3")
    }

    @Test
    fun `rejects an unknown absence charge`() {
        val text = BackupCodec.encode(snapshot)
        assertTrue("fixture must hold a late cancellation", "\"absenceCharge\": \"LATE_CANCELLATION\"" in text)
        val broken = text.replace("\"absenceCharge\": \"LATE_CANCELLATION\"", "\"absenceCharge\": \"SOMETIMES\"")

        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(broken) }

        assertEquals("Lesson 9 has an unknown absence charge 'SOMETIMES'", error.message)
    }

    @Test
    fun `reports the missing field`() {
        val broken = BackupCodec.encode(snapshot).replace("\"feeCents\"", "\"fee\"")
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(broken) }
        assertEquals("Missing field 'feeCents'", error.message)
    }

    @Test
    fun `round trips the reminder switches and defaults them in older files`() {
        val reminders = ReminderSettings(enabled = true, earlyDecember = false, earlyJune = true, earlyEaster = false)
        val withReminders = snapshot.copy(settings = snapshot.settings.copy(reminders = reminders))
        assertEquals(withReminders, BackupCodec.decode(BackupCodec.encode(withReminders)))
        val off = snapshot.copy(settings = snapshot.settings.copy(reminders = ReminderSettings(enabled = false)))
        assertEquals(off, BackupCodec.decode(BackupCodec.encode(off)))

        val older = BackupCodec.encode(withReminders).replace(Regex(",\\s*\"remind[A-Za-z]+\": (true|false)"), "")
        assertTrue("fixture must not mention reminders", "remind" !in older)
        assertEquals(ReminderSettings(), BackupCodec.decode(older).settings.reminders)

        val broken = BackupCodec.encode(withReminders).replace("\"remindEarlyJune\": true", "\"remindEarlyJune\": 1")
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(broken) }
        assertEquals("Invalid value for 'remindEarlyJune'", error.message)
    }

    @Test
    fun `in-memory repository restores a snapshot`() = runTest {
        val repos = InMemoryRepositories()
        repos.replaceAll(snapshot)
        val restored = repos.readAll()
        assertEquals(snapshot.copy(exportedAt = restored.exportedAt), restored)
        assertEquals(10L, repos.saveStudent(student()))
    }

    private fun BackupSnapshot.withoutDocuments() = copy(invoices = invoices.map { it.copy(document = null) })

    /** This snapshot as an older app wrote it: the version 4 keys, which hold only defaults here, left out. */
    private fun BackupSnapshot.encodedAs(version: Int): String {
        require(lessons.all { it.absenceCharge == AbsenceCharge.NONE && it.absenceNote.isEmpty() })
        require(!settings.showAddressOnStatements)
        return BackupCodec.encode(this)
            .replace("\"version\": 4", "\"version\": $version")
            .replace(Regex(",\\s*\"absenceCharge\": \"NONE\""), "")
            .replace(Regex(",\\s*\"absenceNote\": \"\""), "")
            .replace(Regex(",\\s*\"showAddressOnStatements\": false"), "")
    }
}
