package gr.eduinvoice.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import gr.eduinvoice.core.model.DomainException
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.Money
import gr.eduinvoice.core.model.Rate
import gr.eduinvoice.core.model.RateType
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.model.StudentGroup
import gr.eduinvoice.core.testing.InMemorySettingsRepository
import gr.eduinvoice.core.usecase.CreateInvoice
import gr.eduinvoice.core.usecase.RescheduleLessons
import gr.eduinvoice.core.usecase.VoidInvoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/** Runs the real SQLite repositories (in-memory database) on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class SqlRepositoriesTest {
    private lateinit var db: EduDatabase
    private lateinit var students: SqlStudentRepository
    private lateinit var groups: SqlGroupRepository
    private lateinit var lessons: SqlLessonRepository
    private lateinit var invoices: SqlInvoiceRepository

    private val march31 = LocalDate.parse("2026-03-31")

    @Before
    fun setUp() {
        db = EduDatabase(ApplicationProvider.getApplicationContext(), name = null)
        students = SqlStudentRepository(db)
        groups = SqlGroupRepository(db)
        lessons = SqlLessonRepository(db)
        invoices = SqlInvoiceRepository(db)
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
        val create = CreateInvoice(lessons, invoices)

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

    @Test
    fun backupRestoreReplacesEverything() = runTest {
        val maria = student()
        val lessonId = lessons.saveLesson(lesson(maria))
        CreateInvoice(lessons, invoices)(maria, listOf(lessonId), march31)
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

        val upgraded = EduDatabase(context, name = name)
        try {
            val upgradedInvoices = SqlInvoiceRepository(upgraded)
            val invoice: Invoice = upgradedInvoices.observeInvoices().first().single()
            assertEquals("2026-0001", invoice.number)
            assertNull(invoice.voidedOn)
            assertEquals(1L, SqlLessonRepository(upgraded).getLessonsOfInvoice(invoice.id).single().studentId)

            upgradedInvoices.voidInvoice(invoice.id, LocalDate.parse("2026-04-01"))
            assertEquals(LocalDate.parse("2026-04-01"), upgradedInvoices.getInvoice(invoice.id)?.voidedOn)
            assertEquals(EduDatabase.VERSION, upgraded.readableDatabase.version)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    private companion object {
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
