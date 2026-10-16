package gr.edusuite.invoicer.data

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import gr.edusuite.invoicer.core.model.AbsenceCharge
import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.InvoicePayment
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Rate
import gr.edusuite.invoicer.core.model.RateType
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.report.Reports
import gr.edusuite.invoicer.core.testing.InMemorySettingsRepository
import gr.edusuite.invoicer.core.usecase.CreateInvoice
import gr.edusuite.invoicer.core.usecase.DeleteGroup
import gr.edusuite.invoicer.core.usecase.DeleteLesson
import gr.edusuite.invoicer.core.usecase.DeleteStudent
import gr.edusuite.invoicer.core.usecase.GetInvoiceDocument
import gr.edusuite.invoicer.core.usecase.InvoiceRenderer
import gr.edusuite.invoicer.core.usecase.RescheduleLessons
import gr.edusuite.invoicer.core.usecase.VoidInvoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Runs the real SQLite repositories (in-memory database) on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class SqlRepositoriesTest {
    private lateinit var db: InvoicerDatabase
    private lateinit var students: SqlStudentRepository
    private lateinit var groups: SqlGroupRepository
    private lateinit var lessons: SqlLessonRepository
    private lateinit var invoices: SqlInvoiceRepository
    private val settings = InMemorySettingsRepository()
    private lateinit var renderer: InvoiceRenderer
    private lateinit var createInvoice: CreateInvoice

    private val march31 = LocalDate.parse("2026-03-31")

    @Before
    fun setUp() {
        db = InvoicerDatabase(ApplicationProvider.getApplicationContext(), name = null)
        students = SqlStudentRepository(db)
        groups = SqlGroupRepository(db)
        lessons = SqlLessonRepository(db)
        invoices = SqlInvoiceRepository(db)
        renderer = InvoiceRenderer(students, groups, settings) { Locale.UK }
        createInvoice = CreateInvoice(lessons, invoices, renderer)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun student(name: String = "Maria") =
        students.saveStudent(Student(firstName = name, rate = Rate(Money(2000), RateType.HOURLY)))

    private fun lesson(studentId: Long, date: String = "2026-03-10", fee: Long = 2000) = Lesson(
        studentId = studentId,
        date = LocalDate.parse(date),
        startTime = LocalTime.of(17, 0),
        durationMinutes = 60,
        fee = Money(fee),
    )

    @Test
    fun savesAndObservesStudentsAndLessons() = runTest {
        val maria = student()
        val id = lessons.saveLesson(lesson(maria))

        assertEquals("Maria", students.observeStudents().first().single().firstName)
        val stored = lessons.observeLessonsOfStudent(maria).first().single()
        assertEquals(id, stored.id)
        assertEquals(LocalTime.of(17, 0), stored.startTime)
        assertEquals(1, lessons.observeLessonsBetween(LocalDate.parse("2026-03-01"), march31).first().size)
        assertEquals(0, lessons.observeLessonsBetween(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")).first().size)
    }

    @Test
    fun invoiceLinksLessonsOnceAndVoidKeepsTheNumber() = runTest {
        val maria = student()
        val a = lessons.saveLesson(lesson(maria))
        val b = lessons.saveLesson(lesson(maria, fee = 1500))
        val create = createInvoice

        val invoice = create(maria, listOf(a, b), march31, markPaid = true)
        assertEquals("2026-0001", invoice.number)
        assertEquals(Money(3500), invoices.getInvoice(invoice.id)?.total)
        assertTrue(lessons.getLessonsOfInvoice(invoice.id).all { it.isPaid && it.invoiceId == invoice.id })

        // The SQL guard rejects a second link even if the use-case checks were bypassed.
        val error = runCatching { invoices.createInvoice(invoice.copy(id = 0, number = "X"), listOf(a), null) }
        assertTrue(error.exceptionOrNull() is DomainException)
        assertNull(invoices.observeInvoices().first().firstOrNull { it.number == "X" })

        VoidInvoice(invoices)(invoice.id, LocalDate.parse("2026-04-02"))
        assertEquals(LocalDate.parse("2026-04-02"), invoices.getInvoice(invoice.id)?.voidedOn)
        assertTrue(lessons.getLessons(listOf(a, b)).all { it.invoiceId == null && it.isPaid })
        assertEquals("2026-0002", create(maria, listOf(a), march31).number)
    }

    @Test
    fun groupsKeepMembersAndDeletingAGroupKeepsItsLessons() = runTest {
        val maria = student()
        val nikos = student("Nikos")
        val groupId = groups.saveGroup(
            StudentGroup(name = "B1", rate = Rate(Money(1200), RateType.HOURLY), memberIds = setOf(maria, nikos)),
        )
        val lessonId = lessons.saveLesson(lesson(maria).copy(groupId = groupId))
        assertEquals(setOf(maria, nikos), groups.getGroup(groupId)?.memberIds)

        groups.deleteGroup(groupId)

        assertNull(groups.getGroup(groupId))
        assertNull(lessons.getLesson(lessonId)?.groupId)
    }

    @Test
    fun rescheduleUpdatesAllSelectedLessonsAtomically() = runTest {
        val maria = student()
        val ids = listOf(lessons.saveLesson(lesson(maria)), lessons.saveLesson(lesson(maria, "2026-03-11")))

        RescheduleLessons(lessons)(ids, LocalDate.parse("2026-03-20"), LocalTime.of(9, 30))

        assertTrue(lessons.getLessons(ids).all { it.date == LocalDate.parse("2026-03-20") && it.startTime == LocalTime.of(9, 30) })
    }

    private fun lateCancel(studentId: Long, fee: Long = 2000, note: String = "") =
        lesson(studentId, fee = fee).copy(
            absent = true,
            absenceCharge = AbsenceCharge.LATE_CANCELLATION,
            absenceNote = note,
        )

    @Test
    fun backupRestoreReplacesEverything() = runTest {
        val maria = student()
        val lessonId = lessons.saveLesson(lesson(maria))
        val excused = lessons.saveLesson(lesson(maria, "2026-03-11").copy(absent = true, absenceNote = "ill"))
        lessons.saveLesson(lateCancel(maria, note = "cancelled at 16:00").copy(paidOn = march31))
        createInvoice(maria, listOf(lessonId, excused), march31)
        val backups = SqlBackupRepository(db, InMemorySettingsRepository())
        val snapshot = backups.readAll()

        student("Someone else")
        backups.replaceAll(snapshot)

        val restored = backups.readAll()
        assertEquals(snapshot.students, restored.students)
        assertEquals(snapshot.lessons, restored.lessons)
        assertEquals(snapshot.invoices, restored.invoices)
        // New rows continue after the restored ids instead of colliding with them.
        assertTrue(student("After restore") > snapshot.students.maxOf { it.id })
    }

    @Test
    fun settingsDropTheRemovedTaxIdWhenLoaded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = "settings-tax-id-test"
        val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
        prefs.edit(commit = true) {
            clear()
            putString("business_name", "Tutor")
            putString("tax_id", "123456789")
        }
        try {
            val settings = PrefsSettingsRepository(context, file).settings.value
            assertEquals("Tutor", settings.business.name)
            assertFalse(prefs.contains("tax_id"))

            // Loading again finds nothing to remove and keeps the rest.
            assertEquals("Tutor", PrefsSettingsRepository(context, file).settings.value.business.name)
            assertFalse(prefs.contains("tax_id"))
        } finally {
            context.deleteSharedPreferences(file)
        }
    }

    @Test
    fun upgradesAVersion1DatabaseWithoutLosingData() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { v1 ->
            V1_SCHEMA.forEach(v1::execSQL)
            v1.execSQL("INSERT INTO students (first_name, rate_cents, rate_type) VALUES ('Maria', 2000, 'HOURLY')")
            v1.execSQL(
                "INSERT INTO invoices (number, student_id, issue_date, total_cents) VALUES ('2026-0001', 1, '2026-03-31', 2000)",
            )
            v1.execSQL(
                "INSERT INTO lessons (student_id, date, start_time, duration_minutes, fee_cents, invoice_id) " +
                    "VALUES (1, '2026-03-10', '17:00', 60, 2000, 1)",
            )
            v1.version = 1
        }

        val upgraded = InvoicerDatabase(context, name = name)
        try {
            val upgradedInvoices = SqlInvoiceRepository(upgraded)
            val invoice: Invoice = upgradedInvoices.observeInvoices().first().single()
            assertEquals("2026-0001", invoice.number)
            assertNull(invoice.voidedOn)
            assertNull(invoice.document)
            assertEquals(1L, SqlLessonRepository(upgraded).getLessonsOfInvoice(invoice.id).single().studentId)

            upgradedInvoices.voidInvoice(invoice.id, LocalDate.parse("2026-04-01"))
            assertEquals(LocalDate.parse("2026-04-01"), upgradedInvoices.getInvoice(invoice.id)?.voidedOn)
            assertEquals(InvoicerDatabase.VERSION, upgraded.readableDatabase.version)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun upgradesAVersion2DatabaseAndFreezesOldInvoicesWhenFirstShared() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v2-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { v2 ->
            V1_SCHEMA.forEach(v2::execSQL)
            V2_CHANGES.forEach(v2::execSQL)
            v2.execSQL("INSERT INTO students (first_name, rate_cents, rate_type) VALUES ('Maria', 2000, 'HOURLY')")
            v2.execSQL(
                "INSERT INTO invoices (number, student_id, issue_date, total_cents) VALUES ('2026-0001', 1, '2026-03-31', 2000)",
            )
            v2.execSQL(
                "INSERT INTO lessons (student_id, date, start_time, duration_minutes, fee_cents, invoice_id) " +
                    "VALUES (1, '2026-03-10', '17:00', 60, 2000, 1)",
            )
            v2.version = 2
        }

        val upgraded = InvoicerDatabase(context, name = name)
        try {
            val upgradedInvoices = SqlInvoiceRepository(upgraded)
            val renderer = InvoiceRenderer(SqlStudentRepository(upgraded), SqlGroupRepository(upgraded), settings) {
                Locale.UK
            }
            val getDocument = GetInvoiceDocument(SqlLessonRepository(upgraded), upgradedInvoices, renderer)
            assertNull(upgradedInvoices.getInvoice(1)?.document)

            val shared = getDocument(1)
            settings.update { it.copy(invoiceFooter = "Changed later") }

            assertEquals(shared, upgradedInvoices.getInvoice(1)?.document)
            assertEquals(shared, getDocument(1))
            assertEquals(InvoicerDatabase.VERSION, upgraded.readableDatabase.version)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun aggregatesMatchTheRulesInCore() = runTest {
        val maria = student()
        val nikos = student("Nikos")
        val a = lessons.saveLesson(lesson(maria, fee = 2000))
        val b = lessons.saveLesson(lesson(maria, fee = 1500).copy(paidOn = march31))
        val excused = lessons.saveLesson(lesson(maria, fee = 2000).copy(absent = true))
        val late = lessons.saveLesson(lateCancel(maria, fee = 2500))
        lessons.saveLesson(lesson(nikos, fee = 3000))
        lessons.saveLesson(lesson(nikos, fee = 900).copy(absent = true))
        lessons.saveLesson(lateCancel(nikos, fee = 1200))
        lessons.saveLesson(lesson(nikos, fee = 0))
        val invoice = createInvoice(maria, listOf(a, b, excused, late), march31)
        val onlyExcused = lessons.saveLesson(lesson(nikos, "2026-03-20", fee = 1500).copy(absent = true))
        val zero = createInvoice(nikos, listOf(onlyExcused), march31)
        val all = lessons.observeLessonsOfStudent(maria).first() + lessons.observeLessonsOfStudent(nikos).first()

        assertEquals(Reports.balances(all), lessons.observeBalances().first())
        assertEquals(Money(4200), lessons.observeBalances().first().single { it.studentId == nikos }.outstanding)
        val payments = lessons.observeInvoicePayments().first()
        assertEquals(mapOf(invoice.id to InvoicePayment(4, 2), zero.id to InvoicePayment(1, 0)), payments)
        assertEquals(Money.ZERO, zero.total)
        assertTrue("a statement of excused absences is paid at once", payments.getValue(zero.id).isPaid)
    }

    @Test
    fun absencesGoOnStatementsAndOnlyChargedLessonsArePaid() = runTest {
        val maria = student()
        val excused = lessons.saveLesson(lesson(maria).copy(absent = true, absenceNote = "ill"))
        val late = lessons.saveLesson(lateCancel(maria))
        val free = lessons.saveLesson(lesson(maria, fee = 0))

        // The SQL guard no longer refuses absences; markPaid pays only what is charged.
        val invoice = createInvoice(maria, listOf(excused, late), march31, markPaid = true)
        assertEquals(Money(2000), invoice.total)
        assertTrue(lessons.getLessons(listOf(excused, late)).all { it.invoiceId == invoice.id })
        assertNull(lessons.getLesson(excused)?.paidOn)
        assertEquals(march31, lessons.getLesson(late)?.paidOn)
        assertEquals("ill", lessons.getLesson(excused)?.absenceNote)

        lessons.setPaid(listOf(excused, free), march31)
        assertNull("setPaid skips an excused absence", lessons.getLesson(excused)?.paidOn)
        assertNull("setPaid skips a free lesson", lessons.getLesson(free)?.paidOn)
    }

    @Test
    fun homeCountsSkipVoidAndFullyPaidInvoices() = runTest {
        val maria = student()
        val open = lessons.saveLesson(lesson(maria, fee = 2000))
        val paid = lessons.saveLesson(lesson(maria, fee = 3000).copy(paidOn = march31))
        val voided = lessons.saveLesson(lesson(maria, fee = 4000))
        lessons.saveLesson(lesson(maria, fee = 5000))
        lessons.saveLesson(lesson(maria, fee = 1500).copy(absent = true))
        val unpaidInvoice = createInvoice(maria, listOf(open), march31)
        val paidInvoice = createInvoice(maria, listOf(paid), march31)
        val voidInvoice = createInvoice(maria, listOf(voided), march31)
        VoidInvoice(invoices)(voidInvoice.id, march31)

        assertEquals(5, lessons.observeLessonCount().first())
        // The voided invoice released its lesson, and an absence is counted too.
        assertEquals(3, lessons.observeUninvoicedCount().first())
        assertEquals(3, invoices.observeInvoiceCount().first())
        assertEquals(1, invoices.observeUnpaidCount().first())
        // The same answer as InvoicePayment.isPaid over observeInvoicePayments, for every invoice that is not void.
        val payments = lessons.observeInvoicePayments().first()
        assertEquals(
            listOf(unpaidInvoice.id),
            invoices.observeInvoices().first().filter { !it.isVoid && payments[it.id]?.isPaid == false }.map { it.id },
        )
        assertTrue(payments.getValue(paidInvoice.id).isPaid)

        lessons.setPaid(listOf(open), march31)
        assertEquals(0, invoices.observeUnpaidCount().first())

        // Only charged lessons can leave a statement unpaid: excused absences never do, late cancellations do.
        val excused = lessons.saveLesson(lesson(maria, fee = 2500).copy(absent = true))
        createInvoice(maria, listOf(excused), march31)
        assertEquals(0, invoices.observeUnpaidCount().first())
        val late = lessons.saveLesson(lateCancel(maria, fee = 2500))
        createInvoice(maria, listOf(late), march31)
        assertEquals(1, invoices.observeUnpaidCount().first())
    }

    @Test
    fun deleteRulesHoldOnSqlite() = runTest {
        val maria = student()
        val groupId = groups.saveGroup(StudentGroup(name = "B1", rate = Rate(Money(1200), RateType.HOURLY)))
        val lessonId = lessons.saveLesson(lesson(maria).copy(groupId = groupId))
        val invoice = createInvoice(maria, listOf(lessonId), march31)

        val groupError = runCatching { DeleteGroup(groups, lessons)(groupId) }.exceptionOrNull()
        assertTrue("$groupError", groupError is DomainException)
        assertEquals(groupId, lessons.getLesson(lessonId)?.groupId)

        VoidInvoice(invoices)(invoice.id, march31)
        DeleteLesson(lessons)(lessonId)
        val studentError = runCatching { DeleteStudent(students, lessons, invoices)(maria) }.exceptionOrNull()
        assertTrue("a void invoice keeps its student: $studentError", studentError is DomainException)
        assertNotNull(students.getStudent(maria))
    }

    @Test
    fun anIssuedDocumentIsStoredOnceAndNeverReplaced() = runTest {
        val maria = student()
        val invoice = createInvoice(maria, listOf(lessons.saveLesson(lesson(maria))), march31)
        val issued = invoice.document!!

        invoices.setDocument(invoice.id, issued.copy(total = "€999.00"))

        assertEquals(issued, invoices.getInvoice(invoice.id)?.document)
    }

    @Test
    fun upgradesAVersion3DatabaseWithoutLosingData() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v3-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { v3 ->
            V1_SCHEMA.forEach(v3::execSQL)
            V2_CHANGES.forEach(v3::execSQL)
            V3_CHANGES.forEach(v3::execSQL)
            v3.execSQL("INSERT INTO students (first_name, rate_cents, rate_type) VALUES ('Maria', 2000, 'HOURLY')")
            v3.execSQL(
                "INSERT INTO invoices (number, student_id, issue_date, total_cents, document) " +
                    "VALUES ('2026-0001', 1, '2026-03-31', 2000, NULL)",
            )
            v3.execSQL(
                "INSERT INTO lessons (student_id, date, start_time, duration_minutes, fee_cents, paid_on, " +
                    "invoice_id) VALUES (1, '2026-03-10', '17:00', 60, 2000, '2026-03-12', 1)",
            )
            v3.execSQL(
                "INSERT INTO lessons (student_id, date, start_time, duration_minutes, fee_cents, absent, notes) " +
                    "VALUES (1, '2026-03-17', '17:00', 60, 2000, 1, 'sick')",
            )
            v3.version = 3
        }

        val upgraded = InvoicerDatabase(context, name = name)
        try {
            val upgradedLessons = SqlLessonRepository(upgraded)
            val (absent, held) = upgradedLessons.observeLessonsOfStudent(1).first()
            assertEquals(LocalDate.parse("2026-03-12"), held.paidOn)
            assertEquals(1L, held.invoiceId)
            assertEquals(AbsenceCharge.NONE, held.absenceCharge)
            assertTrue("an old absence stays excused", absent.isExcused)
            assertEquals("sick", absent.notes)
            assertEquals("", absent.absenceNote)
            assertEquals(Money.ZERO, absent.charge)
            assertEquals("2026-0001", SqlInvoiceRepository(upgraded).getInvoice(1)?.number)

            // The new columns work after the upgrade.
            val id = upgradedLessons.saveLesson(lateCancel(1, note = "late"))
            assertEquals(AbsenceCharge.LATE_CANCELLATION, upgradedLessons.getLesson(id)?.absenceCharge)
            assertEquals("late", upgradedLessons.getLesson(id)?.absenceNote)
            assertEquals(InvoicerDatabase.VERSION, upgraded.readableDatabase.version)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    private companion object {
        /** Frozen copy of the version 3 changes (2.0.0-alpha03). Never edit. */
        val V3_CHANGES = listOf("ALTER TABLE invoices ADD COLUMN document TEXT")

        /** Frozen copy of the version 2 changes (2.0.0-alpha02). Never edit. */
        val V2_CHANGES = listOf("ALTER TABLE invoices ADD COLUMN voided_on TEXT")

        /** Frozen copy of schema version 1 (2.0.0-alpha01). Never edit: it is what users upgrade from. */
        val V1_SCHEMA = listOf(
            "CREATE TABLE students (id INTEGER PRIMARY KEY AUTOINCREMENT, first_name TEXT NOT NULL, " +
                "last_name TEXT NOT NULL DEFAULT '', class_name TEXT NOT NULL DEFAULT '', " +
                "guardian_name TEXT NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '', " +
                "email TEXT NOT NULL DEFAULT '', rate_cents INTEGER NOT NULL CHECK (rate_cents >= 0), " +
                "rate_type TEXT NOT NULL CHECK (rate_type IN ('HOURLY', 'PER_LESSON')), " +
                "notes TEXT NOT NULL DEFAULT '', archived INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE student_groups (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "class_name TEXT NOT NULL DEFAULT '', rate_cents INTEGER NOT NULL CHECK (rate_cents >= 0), " +
                "rate_type TEXT NOT NULL CHECK (rate_type IN ('HOURLY', 'PER_LESSON')), " +
                "archived INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE group_members (group_id INTEGER NOT NULL REFERENCES student_groups (id) ON DELETE CASCADE, " +
                "student_id INTEGER NOT NULL REFERENCES students (id) ON DELETE CASCADE, " +
                "PRIMARY KEY (group_id, student_id))",
            "CREATE INDEX idx_group_members_student ON group_members (student_id)",
            "CREATE TABLE invoices (id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL UNIQUE, " +
                "student_id INTEGER NOT NULL REFERENCES students (id) ON DELETE RESTRICT, " +
                "issue_date TEXT NOT NULL, total_cents INTEGER NOT NULL, notes TEXT NOT NULL DEFAULT '')",
            "CREATE INDEX idx_invoices_student ON invoices (student_id)",
            "CREATE TABLE lessons (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "student_id INTEGER NOT NULL REFERENCES students (id) ON DELETE CASCADE, " +
                "group_id INTEGER REFERENCES student_groups (id) ON DELETE SET NULL, date TEXT NOT NULL, " +
                "start_time TEXT NOT NULL, duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0), " +
                "fee_cents INTEGER NOT NULL CHECK (fee_cents >= 0), absent INTEGER NOT NULL DEFAULT 0, " +
                "notes TEXT NOT NULL DEFAULT '', paid_on TEXT, " +
                "invoice_id INTEGER REFERENCES invoices (id) ON DELETE SET NULL)",
            "CREATE INDEX idx_lessons_student ON lessons (student_id)",
            "CREATE INDEX idx_lessons_date ON lessons (date)",
            "CREATE INDEX idx_lessons_group ON lessons (group_id)",
            "CREATE INDEX idx_lessons_invoice ON lessons (invoice_id)",
        )
    }
}
