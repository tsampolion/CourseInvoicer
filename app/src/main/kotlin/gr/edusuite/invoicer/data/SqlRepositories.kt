package gr.edusuite.invoicer.data

import android.database.sqlite.SQLiteDatabase
import gr.edusuite.invoicer.core.invoice.InvoiceDocumentCodec
import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.InvoiceDocument
import gr.edusuite.invoicer.core.model.InvoicePayment
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.report.StudentBalance
import gr.edusuite.invoicer.core.report.UninvoicedSummary
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class SqlStudentRepository(private val db: InvoicerDatabase) : StudentRepository {
    override fun observeStudents(): Flow<List<Student>> = db.observe(Table.STUDENTS) { it.allStudents() }

    override fun observeStudent(id: Long): Flow<Student?> = db.observe(Table.STUDENTS) { it.student(id) }

    override suspend fun getStudent(id: Long): Student? = db.read { it.student(id) }

    override suspend fun saveStudent(student: Student): Long = db.write(Table.STUDENTS) { d ->
        if (student.id == 0L) {
            d.insertOrThrow("students", null, student.toValues())
        } else {
            d.update("students", student.toValues(), "id = ?", arrayOf(student.id.toString()))
            student.id
        }
    }

    override suspend fun deleteStudent(id: Long) {
        db.write(Table.STUDENTS, Table.GROUPS, Table.LESSONS) { d ->
            d.delete("students", "id = ?", arrayOf(id.toString()))
        }
    }
}

internal fun SQLiteDatabase.allStudents(): List<Student> =
    select("SELECT * FROM students ORDER BY first_name COLLATE NOCASE, last_name COLLATE NOCASE, id")
        .readAll { it.toStudent() }

private fun SQLiteDatabase.student(id: Long): Student? =
    select("SELECT * FROM students WHERE id = ?", id).readAll { it.toStudent() }.firstOrNull()

class SqlGroupRepository(private val db: InvoicerDatabase) : GroupRepository {
    override fun observeGroups(): Flow<List<StudentGroup>> = db.observe(Table.GROUPS) { it.allGroups() }

    override fun observeGroup(id: Long): Flow<StudentGroup?> =
        db.observe(Table.GROUPS) { d -> d.allGroups().firstOrNull { it.id == id } }

    override suspend fun getGroup(id: Long): StudentGroup? = db.read { d -> d.allGroups().firstOrNull { it.id == id } }

    override suspend fun saveGroup(group: StudentGroup): Long = db.write(Table.GROUPS) { d ->
        val id = if (group.id == 0L) {
            d.insertOrThrow("student_groups", null, group.toValues())
        } else {
            d.update("student_groups", group.toValues(), "id = ?", arrayOf(group.id.toString()))
            group.id
        }
        d.delete("group_members", "group_id = ?", arrayOf(id.toString()))
        group.memberIds.forEach { studentId -> d.insertMember(id, studentId) }
        id
    }

    override suspend fun deleteGroup(id: Long) {
        db.write(Table.GROUPS, Table.LESSONS) { d ->
            d.delete("student_groups", "id = ?", arrayOf(id.toString()))
        }
    }
}

internal fun SQLiteDatabase.insertMember(groupId: Long, studentId: Long) {
    execSQL("INSERT INTO group_members (group_id, student_id) VALUES (?, ?)", arrayOf(groupId, studentId))
}

internal fun SQLiteDatabase.allGroups(): List<StudentGroup> {
    val members = select("SELECT group_id, student_id FROM group_members")
        .readAll { it.long("group_id") to it.long("student_id") }
        .groupBy({ it.first }, { it.second })
    return select("SELECT * FROM student_groups ORDER BY name COLLATE NOCASE, id")
        .readAll { it.toGroup() }
        .map { group -> group.copy(memberIds = members[group.id].orEmpty().toSet()) }
}

/**
 * SQL for [Lesson.charge] being positive: held, or absent as a late cancellation, and not free. The one
 * definition of a charged lesson in SQL; balances, payments and setPaid all use it (R3, R9, R12).
 */
internal const val CHARGED_LESSON = "((absent = 0 OR absence_charge = 'LATE_CANCELLATION') AND fee_cents > 0)"

class SqlLessonRepository(private val db: InvoicerDatabase) : LessonRepository {
    override fun observeLessonsBetween(from: LocalDate, to: LocalDate): Flow<List<Lesson>> =
        db.observe(Table.LESSONS) { it.lessons("date BETWEEN ? AND ?", from.toString(), to.toString()) }

    override fun observeLessonsOfStudent(studentId: Long): Flow<List<Lesson>> =
        db.observe(Table.LESSONS) { it.lessons("student_id = ?", studentId) }

    // Same rule as Lesson.isOutstanding: unpaid and charged. A charged lesson's charge is its fee.
    override fun observeBalances(): Flow<List<StudentBalance>> = db.observe(Table.LESSONS) { d ->
        d.select(
            "SELECT student_id, SUM(fee_cents) AS owed, COUNT(*) AS open_count FROM lessons " +
                "WHERE paid_on IS NULL AND $CHARGED_LESSON " +
                "GROUP BY student_id ORDER BY owed DESC, student_id",
        ).readAll { StudentBalance(it.long("student_id"), Money(it.long("owed")), it.int("open_count")) }
    }

    // Same rule as InvoicePayment.of: every linked lesson counts, only charged ones can be unpaid.
    override fun observeInvoicePayments(): Flow<Map<Long, InvoicePayment>> = db.observe(Table.LESSONS) { d ->
        d.select(
            "SELECT invoice_id, COUNT(*) AS linked, " +
                "SUM(CASE WHEN paid_on IS NULL AND $CHARGED_LESSON THEN 1 ELSE 0 END) AS unpaid " +
                "FROM lessons WHERE invoice_id IS NOT NULL GROUP BY invoice_id",
        ).readAll { it.long("invoice_id") to InvoicePayment(it.int("linked"), it.int("unpaid")) }.toMap()
    }

    override fun observeLessonCount(): Flow<Int> =
        db.observe(Table.LESSONS) { it.count("SELECT COUNT(*) FROM lessons") }

    override fun observeUninvoicedCount(): Flow<Int> =
        db.observe(Table.LESSONS) { it.count("SELECT COUNT(*) FROM lessons WHERE invoice_id IS NULL") }

    override fun observeUninvoicedSummary(from: LocalDate, to: LocalDate): Flow<UninvoicedSummary> =
        db.observe(Table.LESSONS) { d ->
            d.select(
                "SELECT COUNT(*) AS open_count, COUNT(DISTINCT student_id) AS student_count FROM lessons " +
                    "WHERE invoice_id IS NULL AND date BETWEEN ? AND ?",
                from.toString(),
                to.toString(),
            ).readAll { UninvoicedSummary(it.int("open_count"), it.int("student_count")) }.first()
        }

    override suspend fun getLesson(id: Long): Lesson? = db.read { it.lessons("id = ?", id).firstOrNull() }

    override suspend fun getLessons(ids: Collection<Long>): List<Lesson> = db.read { d ->
        ids.distinct().chunked(InvoicerDatabase.MAX_ARGS).flatMap { chunk ->
            d.lessons("id IN (${placeholders(chunk.size)})", *chunk.toTypedArray())
        }
    }

    override suspend fun getLessonsOfInvoice(invoiceId: Long): List<Lesson> =
        db.read { it.lessons("invoice_id = ?", invoiceId) }

    override suspend fun getLessonsOfGroup(groupId: Long): List<Lesson> =
        db.read { it.lessons("group_id = ?", groupId) }

    override suspend fun countLessonsOfStudent(studentId: Long): Int = db.read { d ->
        d.select("SELECT COUNT(*) FROM lessons WHERE student_id = ?", studentId)
            .readAll { it.getInt(0) }
            .first()
    }

    override suspend fun saveLesson(lesson: Lesson): Long = db.write(Table.LESSONS) { d ->
        if (lesson.id == 0L) {
            d.insertOrThrow("lessons", null, lesson.toValues())
        } else {
            d.update("lessons", lesson.toValues(), "id = ?", arrayOf(lesson.id.toString()))
            lesson.id
        }
    }

    override suspend fun insertLessons(lessons: List<Lesson>): List<Long> = db.write(Table.LESSONS) { d ->
        lessons.map { d.insertOrThrow("lessons", null, it.toValues()) }
    }

    override suspend fun updateLessons(lessons: List<Lesson>) {
        db.write(Table.LESSONS) { d ->
            lessons.forEach { lesson ->
                val updated = d.update("lessons", lesson.toValues(), "id = ?", arrayOf(lesson.id.toString()))
                check(updated == 1) { "Lesson ${lesson.id} no longer exists" }
            }
        }
    }

    override suspend fun restoreLesson(lesson: Lesson) {
        db.write(Table.LESSONS) { d ->
            if (d.lessons("id = ?", lesson.id).isNotEmpty()) throw DomainException("This lesson already exists")
            d.insertOrThrow("lessons", null, lesson.toValues().apply { put("id", lesson.id) })
        }
    }

    override suspend fun restorePaid(previous: Map<Long, LocalDate?>) {
        db.write(Table.LESSONS) { d ->
            val current = previous.keys.chunked(InvoicerDatabase.MAX_ARGS).flatMap { chunk ->
                d.lessons("id IN (${placeholders(chunk.size)})", *chunk.toTypedArray())
            }
            if (current.size != previous.size) throw DomainException("Some lessons no longer exist")
            if (current.any { previous[it.id] != null && !it.charge.isPositive }) {
                throw DomainException("An excused absence is not charged, so it can't be paid")
            }
            previous.forEach { (id, date) ->
                d.execSQL("UPDATE lessons SET paid_on = ? WHERE id = ?", arrayOf<Any?>(date?.toString(), id))
            }
        }
    }

    override suspend fun deleteLesson(id: Long) {
        db.write(Table.LESSONS) { d -> d.delete("lessons", "id = ?", arrayOf(id.toString())) }
    }

    override suspend fun setPaid(ids: Collection<Long>, paidOn: LocalDate?) {
        db.write(Table.LESSONS) { d ->
            ids.distinct().chunked(InvoicerDatabase.MAX_ARGS).forEach { chunk ->
                // Lessons that are not charged are never paid.
                d.execSQL(
                    "UPDATE lessons SET paid_on = ? WHERE id IN (${placeholders(chunk.size)})" +
                        if (paidOn == null) "" else " AND $CHARGED_LESSON",
                    arrayOf<Any?>(paidOn?.toString(), *chunk.toTypedArray()),
                )
            }
        }
    }
}

/** Runs a `SELECT COUNT(*) …` and returns the count. */
private fun SQLiteDatabase.count(sql: String): Int = select(sql).readAll { it.getInt(0) }.first()

internal fun SQLiteDatabase.lessons(where: String, vararg args: Any): List<Lesson> =
    select("SELECT * FROM lessons WHERE $where ORDER BY date DESC, start_time DESC, id", *args)
        .readAll { it.toLesson() }

class SqlInvoiceRepository(private val db: InvoicerDatabase) : InvoiceRepository {
    override fun observeInvoices(): Flow<List<Invoice>> = db.observe(Table.INVOICES) { it.allInvoices() }

    // Same rule as InvoicePayment.isPaid over observeInvoicePayments: an invoice that is not void and has at
    // least one linked lesson that is unpaid. A void invoice has released its lessons, so it never counts.
    override fun observeUnpaidCount(): Flow<Int> = db.observe(Table.INVOICES, Table.LESSONS) { d ->
        d.count(
            "SELECT COUNT(*) FROM invoices i WHERE i.voided_on IS NULL AND EXISTS " +
                "(SELECT 1 FROM lessons l WHERE l.invoice_id = i.id AND l.paid_on IS NULL AND $CHARGED_LESSON)",
        )
    }

    override fun observeInvoiceCount(): Flow<Int> =
        db.observe(Table.INVOICES) { it.count("SELECT COUNT(*) FROM invoices") }

    override suspend fun getInvoice(id: Long): Invoice? = db.read { d ->
        d.select("SELECT * FROM invoices WHERE id = ?", id).readAll { it.toInvoice() }.firstOrNull()
    }

    override suspend fun invoiceNumbers(): List<String> = db.read { d ->
        d.select("SELECT number FROM invoices").readAll { it.getString(0) }
    }

    override suspend fun countInvoicesOfStudent(studentId: Long): Int = db.read { d ->
        d.select("SELECT COUNT(*) FROM invoices WHERE student_id = ?", studentId).readAll { it.getInt(0) }.first()
    }

    override suspend fun createInvoice(invoice: Invoice, lessonIds: Collection<Long>, paidOn: LocalDate?): Long =
        db.write(Table.INVOICES, Table.LESSONS) { d ->
            val invoiceId = d.insertOrThrow("invoices", null, invoice.toValues())
            lessonIds.distinct().chunked(InvoicerDatabase.MAX_ARGS).forEach { chunk ->
                val linked = d.compileStatement(
                    "UPDATE lessons SET invoice_id = ? " +
                        "WHERE id IN (${placeholders(chunk.size)}) AND invoice_id IS NULL",
                ).use { statement ->
                    statement.bindLong(1, invoiceId)
                    chunk.forEachIndexed { index, id -> statement.bindLong(index + 2, id) }
                    statement.executeUpdateDelete()
                }
                // Throwing rolls the whole transaction back, so a lesson is never invoiced twice.
                if (linked != chunk.size) throw DomainException("Some lessons are already on a statement")
                if (paidOn != null) {
                    d.execSQL(
                        "UPDATE lessons SET paid_on = ? " +
                            "WHERE id IN (${placeholders(chunk.size)}) AND paid_on IS NULL AND $CHARGED_LESSON",
                        arrayOf<Any?>(paidOn.toString(), *chunk.toTypedArray()),
                    )
                }
            }
            invoiceId
        }

    override suspend fun voidInvoice(id: Long, on: LocalDate) {
        db.write(Table.INVOICES, Table.LESSONS) { d ->
            d.execSQL("UPDATE lessons SET invoice_id = NULL WHERE invoice_id = ?", arrayOf(id))
            d.execSQL(
                "UPDATE invoices SET voided_on = ? WHERE id = ? AND voided_on IS NULL",
                arrayOf<Any?>(on.toString(), id),
            )
        }
    }

    override suspend fun setDocument(id: Long, document: InvoiceDocument) {
        db.write(Table.INVOICES) { d ->
            d.execSQL(
                "UPDATE invoices SET document = ? WHERE id = ? AND document IS NULL",
                arrayOf<Any?>(InvoiceDocumentCodec.encode(document), id),
            )
        }
    }
}

internal fun SQLiteDatabase.allInvoices(): List<Invoice> =
    select("SELECT * FROM invoices ORDER BY issue_date DESC, number DESC").readAll { it.toInvoice() }
