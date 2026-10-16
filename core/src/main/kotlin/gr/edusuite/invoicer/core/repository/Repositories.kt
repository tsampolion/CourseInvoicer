package gr.edusuite.invoicer.core.repository

import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.InvoiceDocument
import gr.edusuite.invoicer.core.model.InvoicePayment
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.report.StudentBalance
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
    fun observeLessonsBetween(from: LocalDate, to: LocalDate): Flow<List<Lesson>>

    fun observeLessonsOfStudent(studentId: Long): Flow<List<Lesson>>

    /**
     * What each student owes, largest balance first: the same result as [gr.edusuite.invoicer.core.report.Reports.balances]
     * over every lesson, computed without loading the lesson history.
     */
    fun observeBalances(): Flow<List<StudentBalance>>

    /** Payment progress of every invoice that has lessons, by invoice id. */
    fun observeInvoicePayments(): Flow<Map<Long, InvoicePayment>>

    suspend fun getLesson(id: Long): Lesson?

    suspend fun getLessons(ids: Collection<Long>): List<Lesson>

    suspend fun getLessonsOfInvoice(invoiceId: Long): List<Lesson>

    suspend fun getLessonsOfGroup(groupId: Long): List<Lesson>

    suspend fun countLessonsOfStudent(studentId: Long): Int

    suspend fun saveLesson(lesson: Lesson): Long

    /** Inserts all lessons atomically. */
    suspend fun insertLessons(lessons: List<Lesson>): List<Long>

    /** Updates all lessons (matched by id) atomically. */
    suspend fun updateLessons(lessons: List<Lesson>)

    suspend fun deleteLesson(id: Long)

    /** Sets (or clears, when [paidOn] is null) the payment date of the given lessons. */
    suspend fun setPaid(ids: Collection<Long>, paidOn: LocalDate?)
}

interface InvoiceRepository {
    fun observeInvoices(): Flow<List<Invoice>>

    suspend fun getInvoice(id: Long): Invoice?

    suspend fun invoiceNumbers(): List<String>

    suspend fun countInvoicesOfStudent(studentId: Long): Int

    /**
     * Atomically stores [invoice] and links [lessonIds] to it. When [paidOn] is set, lessons that are
     * still unpaid are marked paid on that date.
     */
    suspend fun createInvoice(invoice: Invoice, lessonIds: Collection<Long>, paidOn: LocalDate?): Long

    /**
     * Atomically marks the invoice void on [on] and releases its lessons so they can be invoiced again.
     * The invoice row (and therefore its number) is kept.
     */
    suspend fun voidInvoice(id: Long, on: LocalDate)

    /** Stores the document of an invoice that has none yet. An issued document is never replaced. */
    suspend fun setDocument(id: Long, document: InvoiceDocument)
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    /** Persists the new settings before [settings] emits them. Updates run one at a time and always finish. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
