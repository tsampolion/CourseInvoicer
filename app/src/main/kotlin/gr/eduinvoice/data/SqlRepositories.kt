package gr.eduinvoice.data

import android.database.sqlite.SQLiteDatabase
import gr.eduinvoice.core.model.DomainException
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.model.StudentGroup
import gr.eduinvoice.core.repository.GroupRepository
import gr.eduinvoice.core.repository.InvoiceRepository
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.StudentRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class SqlStudentRepository(private val db: EduDatabase) : StudentRepository {
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

class SqlGroupRepository(private val db: EduDatabase) : GroupRepository {
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

class SqlLessonRepository(private val db: EduDatabase) : LessonRepository {
    override fun observeLessons(): Flow<List<Lesson>> =
        db.observe(Table.LESSONS) { it.lessons("1 = 1") }

    override fun observeLessonsBetween(from: LocalDate, to: LocalDate): Flow<List<Lesson>> =
        db.observe(Table.LESSONS) { it.lessons("date BETWEEN ? AND ?", from.toString(), to.toString()) }

    override fun observeLessonsOfStudent(studentId: Long): Flow<List<Lesson>> =
        db.observe(Table.LESSONS) { it.lessons("student_id = ?", studentId) }

    override suspend fun getLesson(id: Long): Lesson? = db.read { it.lessons("id = ?", id).firstOrNull() }

    override suspend fun getLessons(ids: Collection<Long>): List<Lesson> = db.read { d ->
        ids.distinct().chunked(EduDatabase.MAX_ARGS).flatMap { chunk ->
            d.lessons("id IN (${placeholders(chunk.size)})", *chunk.toTypedArray())
        }
    }

    override suspend fun getLessonsOfInvoice(invoiceId: Long): List<Lesson> =
        db.read { it.lessons("invoice_id = ?", invoiceId) }

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

    override suspend fun deleteLesson(id: Long) {
        db.write(Table.LESSONS) { d -> d.delete("lessons", "id = ?", arrayOf(id.toString())) }
    }

    override suspend fun setPaid(ids: Collection<Long>, paidOn: LocalDate?) {
        db.write(Table.LESSONS) { d ->
            ids.distinct().chunked(EduDatabase.MAX_ARGS).forEach { chunk ->
                d.execSQL(
                    "UPDATE lessons SET paid_on = ? WHERE id IN (${placeholders(chunk.size)})",
                    arrayOf<Any?>(paidOn?.toString()) + chunk,
                )
            }
        }
    }
}

internal fun SQLiteDatabase.lessons(where: String, vararg args: Any): List<Lesson> =
    select("SELECT * FROM lessons WHERE $where ORDER BY date DESC, start_time DESC, id", *args)
        .readAll { it.toLesson() }

class SqlInvoiceRepository(private val db: EduDatabase) : InvoiceRepository {
    override fun observeInvoices(): Flow<List<Invoice>> = db.observe(Table.INVOICES) { it.allInvoices() }

    override suspend fun getInvoice(id: Long): Invoice? = db.read { d ->
        d.select("SELECT * FROM invoices WHERE id = ?", id).readAll { it.toInvoice() }.firstOrNull()
    }

    override suspend fun invoiceNumbers(): List<String> = db.read { d ->
        d.select("SELECT number FROM invoices").readAll { it.getString(0) }
    }

    override suspend fun createInvoice(invoice: Invoice, lessonIds: Collection<Long>, paidOn: LocalDate?): Long =
        db.write(Table.INVOICES, Table.LESSONS) { d ->
            val invoiceId = d.insertOrThrow("invoices", null, invoice.toValues())
            lessonIds.distinct().chunked(EduDatabase.MAX_ARGS).forEach { chunk ->
                val linked = d.compileStatement(
                    "UPDATE lessons SET invoice_id = ? " +
                        "WHERE id IN (${placeholders(chunk.size)}) AND invoice_id IS NULL AND absent = 0",
                ).use { statement ->
                    statement.bindLong(1, invoiceId)
                    chunk.forEachIndexed { index, id -> statement.bindLong(index + 2, id) }
                    statement.executeUpdateDelete()
                }
                // Throwing rolls the whole transaction back, so a lesson is never invoiced twice.
                if (linked != chunk.size) throw DomainException("Some lessons are already on an invoice")
                if (paidOn != null) {
                    d.execSQL(
                        "UPDATE lessons SET paid_on = ? WHERE id IN (${placeholders(chunk.size)}) AND paid_on IS NULL",
                        arrayOf<Any?>(paidOn.toString()) + chunk,
                    )
                }
            }
            invoiceId
        }

    override suspend fun deleteInvoice(id: Long) {
        db.write(Table.INVOICES, Table.LESSONS) { d ->
            d.execSQL("UPDATE lessons SET invoice_id = NULL WHERE invoice_id = ?", arrayOf(id))
            d.delete("invoices", "id = ?", arrayOf(id.toString()))
        }
    }
}

internal fun SQLiteDatabase.allInvoices(): List<Invoice> =
    select("SELECT * FROM invoices ORDER BY issue_date DESC, number DESC").readAll { it.toInvoice() }
