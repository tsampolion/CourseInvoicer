package gr.eduinvoice.core.repository

import gr.eduinvoice.core.model.AppSettings
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.model.StudentGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** Insert when id == 0, update otherwise. Every `observe*` flow re-emits after a relevant write. */
interface StudentRepository {
    fun observeStudents(): Flow<List<Student>>

    fun observeStudent(id: Long): Flow<Student?>

    suspend fun getStudent(id: Long): Student?

    suspend fun saveStudent(student: Student): Long

    suspend fun deleteStudent(id: Long)
}

interface GroupRepository {
    fun observeGroups(): Flow<List<StudentGroup>>

    fun observeGroup(id: Long): Flow<StudentGroup?>

    suspend fun getGroup(id: Long): StudentGroup?

    /** Saves the group together with its [StudentGroup.memberIds]. */
    suspend fun saveGroup(group: StudentGroup): Long

    suspend fun deleteGroup(id: Long)
}

interface LessonRepository {
    fun observeLessons(): Flow<List<Lesson>>

    fun observeLessonsBetween(from: LocalDate, to: LocalDate): Flow<List<Lesson>>

    fun observeLessonsOfStudent(studentId: Long): Flow<List<Lesson>>

    suspend fun getLesson(id: Long): Lesson?

    suspend fun getLessons(ids: Collection<Long>): List<Lesson>

    suspend fun getLessonsOfInvoice(invoiceId: Long): List<Lesson>

    suspend fun countLessonsOfStudent(studentId: Long): Int

    suspend fun saveLesson(lesson: Lesson): Long

    /** Inserts all lessons atomically. */
    suspend fun insertLessons(lessons: List<Lesson>): List<Long>

    suspend fun deleteLesson(id: Long)

    /** Sets (or clears, when [paidOn] is null) the payment date of the given lessons. */
    suspend fun setPaid(ids: Collection<Long>, paidOn: LocalDate?)
}

interface InvoiceRepository {
    fun observeInvoices(): Flow<List<Invoice>>

    suspend fun getInvoice(id: Long): Invoice?

    suspend fun invoiceNumbers(): List<String>

    /**
     * Atomically stores [invoice] and links [lessonIds] to it. When [paidOn] is set, lessons that are
     * still unpaid are marked paid on that date.
     */
    suspend fun createInvoice(invoice: Invoice, lessonIds: Collection<Long>, paidOn: LocalDate?): Long

    /** Atomically deletes the invoice and releases its lessons so they can be invoiced again. */
    suspend fun deleteInvoice(id: Long)
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
