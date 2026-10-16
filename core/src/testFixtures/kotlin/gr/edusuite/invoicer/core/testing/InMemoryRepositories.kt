package gr.edusuite.invoicer.core.testing

import gr.edusuite.invoicer.core.backup.BackupRepository
import gr.edusuite.invoicer.core.backup.BackupSnapshot
import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.InvoiceDocument
import gr.edusuite.invoicer.core.model.InvoicePayment
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.report.Reports
import gr.edusuite.invoicer.core.report.StudentBalance
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.SettingsRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * In-memory implementation of every repository for tests. It mirrors the SQLite schema's integrity rules
 * (foreign keys, the invoice link guard), so a use case that relies on them fails here as on a device.
 */
class InMemoryRepositories :
    StudentRepository,
    GroupRepository,
    LessonRepository,
    InvoiceRepository,
    BackupRepository {
    private data class Data(
        val students: Map<Long, Student> = emptyMap(),
        val groups: Map<Long, StudentGroup> = emptyMap(),
        val lessons: Map<Long, Lesson> = emptyMap(),
        val invoices: Map<Long, Invoice> = emptyMap(),
        val nextId: Long = 1,
    )

    private val data = MutableStateFlow(Data())

    val settingsRepository = InMemorySettingsRepository()

    val students: List<Student> get() = data.value.students.values.sortedBy { it.id }
    val groups: List<StudentGroup> get() = data.value.groups.values.sortedBy { it.id }
    val lessons: List<Lesson> get() = data.value.lessons.values.sortedBy { it.id }
    val invoices: List<Invoice> get() = data.value.invoices.values.sortedBy { it.id }

    private fun allocateId(): Long {
        var id = 0L
        data.update {
            id = it.nextId
            it.copy(nextId = it.nextId + 1)
        }
        return id
    }

    // Students

    override fun observeStudents(): Flow<List<Student>> = data.map { d ->
        d.students.values.sortedWith(compareBy({ it.firstName.lowercase() }, { it.lastName.lowercase() }))
    }

    override fun observeStudent(id: Long): Flow<Student?> = data.map { it.students[id] }

    override suspend fun getStudent(id: Long): Student? = data.value.students[id]

    override suspend fun saveStudent(student: Student): Long {
        val id = if (student.id == 0L) allocateId() else student.id
        data.update { it.copy(students = it.students + (id to student.copy(id = id))) }
        return id
    }

    override suspend fun deleteStudent(id: Long) {
        // invoices.student_id is ON DELETE RESTRICT.
        check(data.value.invoices.values.none { it.studentId == id }) { "FOREIGN KEY constraint failed" }
        data.update { d ->
            d.copy(
                students = d.students - id,
                groups = d.groups.mapValues { (_, group) -> group.copy(memberIds = group.memberIds - id) },
                lessons = d.lessons.filterValues { it.studentId != id },
            )
        }
    }

    // Groups

    override fun observeGroups(): Flow<List<StudentGroup>> = data.map { d ->
        d.groups.values.sortedBy { it.name.lowercase() }
    }

    override fun observeGroup(id: Long): Flow<StudentGroup?> = data.map { it.groups[id] }

    override suspend fun getGroup(id: Long): StudentGroup? = data.value.groups[id]

    override suspend fun saveGroup(group: StudentGroup): Long {
        val id = if (group.id == 0L) allocateId() else group.id
        data.update { it.copy(groups = it.groups + (id to group.copy(id = id))) }
        return id
    }

    override suspend fun deleteGroup(id: Long) {
        data.update { d ->
            d.copy(
                groups = d.groups - id,
                lessons = d.lessons.mapValues { (_, l) -> if (l.groupId == id) l.copy(groupId = null) else l },
            )
        }
    }

    // Lessons

    private val lessonOrder = compareByDescending<Lesson> { it.date }.thenByDescending { it.startTime }.thenBy { it.id }

    override fun observeLessonsBetween(from: LocalDate, to: LocalDate): Flow<List<Lesson>> =
        data.map { d -> d.lessons.values.filter { it.date in from..to }.sortedWith(lessonOrder) }

    override fun observeLessonsOfStudent(studentId: Long): Flow<List<Lesson>> =
        data.map { d -> d.lessons.values.filter { it.studentId == studentId }.sortedWith(lessonOrder) }

    override fun observeBalances(): Flow<List<StudentBalance>> =
        data.map { Reports.balances(it.lessons.values.toList()) }

    override fun observeInvoicePayments(): Flow<Map<Long, InvoicePayment>> = data.map { d ->
        d.lessons.values.filter { it.invoiceId != null }
            .groupBy { it.invoiceId!! }
            .mapValues { (_, linked) -> InvoicePayment.of(linked) }
    }

    override fun observeLessonCount(): Flow<Int> = data.map { it.lessons.size }

    override fun observeUninvoicedCount(): Flow<Int> = data.map { d -> d.lessons.values.count { !it.isInvoiced } }

    override suspend fun getLesson(id: Long): Lesson? = data.value.lessons[id]

    override suspend fun getLessons(ids: Collection<Long>): List<Lesson> = ids.mapNotNull { data.value.lessons[it] }

    override suspend fun getLessonsOfInvoice(invoiceId: Long): List<Lesson> =
        data.value.lessons.values.filter { it.invoiceId == invoiceId }.sortedWith(lessonOrder)

    override suspend fun getLessonsOfGroup(groupId: Long): List<Lesson> =
        data.value.lessons.values.filter { it.groupId == groupId }.sortedWith(lessonOrder)

    override suspend fun countLessonsOfStudent(studentId: Long): Int =
        data.value.lessons.values.count { it.studentId == studentId }

    override suspend fun saveLesson(lesson: Lesson): Long {
        check(lesson.studentId in data.value.students) { "FOREIGN KEY constraint failed" }
        val id = if (lesson.id == 0L) allocateId() else lesson.id
        data.update { it.copy(lessons = it.lessons + (id to lesson.copy(id = id))) }
        return id
    }

    override suspend fun insertLessons(lessons: List<Lesson>): List<Long> = lessons.map { saveLesson(it.copy(id = 0)) }

    override suspend fun updateLessons(lessons: List<Lesson>) {
        check(lessons.all { it.id in data.value.lessons }) { "Unknown lesson" }
        data.update { d -> d.copy(lessons = d.lessons + lessons.associateBy { it.id }) }
    }

    override suspend fun deleteLesson(id: Long) {
        data.update { it.copy(lessons = it.lessons - id) }
    }

    // Like the SQL update, rows that are not charged (excused absences, free lessons) are left alone.
    override suspend fun setPaid(ids: Collection<Long>, paidOn: LocalDate?) {
        data.update { d ->
            d.copy(
                lessons = d.lessons.mapValues { (id, l) ->
                    if (id in ids && l.charge.isPositive) l.copy(paidOn = paidOn) else l
                },
            )
        }
    }

    // Invoices

    override fun observeInvoices(): Flow<List<Invoice>> = data.map { d ->
        d.invoices.values.sortedWith(compareByDescending<Invoice> { it.issueDate }.thenByDescending { it.number })
    }

    override fun observeInvoiceCount(): Flow<Int> = data.map { it.invoices.size }

    override fun observeUnpaidCount(): Flow<Int> = data.map { d ->
        val linked = d.lessons.values.filter { it.invoiceId != null }.groupBy { it.invoiceId!! }
        d.invoices.values.count { invoice ->
            !invoice.isVoid && linked[invoice.id]?.let { !InvoicePayment.of(it).isPaid } == true
        }
    }

    override suspend fun getInvoice(id: Long): Invoice? = data.value.invoices[id]

    override suspend fun invoiceNumbers(): List<String> = data.value.invoices.values.map { it.number }

    override suspend fun countInvoicesOfStudent(studentId: Long): Int =
        data.value.invoices.values.count { it.studentId == studentId }

    override suspend fun createInvoice(invoice: Invoice, lessonIds: Collection<Long>, paidOn: LocalDate?): Long {
        check(data.value.invoices.values.none { it.number == invoice.number }) { "UNIQUE constraint failed" }
        // Same all-or-nothing guard as the SQL update: every lesson must exist and be on no other statement.
        val linkable = lessonIds.distinct().map { data.value.lessons[it] }
        if (linkable.any { it == null || it.invoiceId != null }) {
            throw DomainException("Some lessons are already on a statement")
        }
        val id = allocateId()
        data.update { d ->
            d.copy(
                invoices = d.invoices + (id to invoice.copy(id = id)),
                lessons = d.lessons.mapValues { (lessonId, l) ->
                    when {
                        lessonId !in lessonIds -> l
                        // Only charged lessons are marked paid, as in the SQL update.
                        l.charge.isPositive -> l.copy(invoiceId = id, paidOn = l.paidOn ?: paidOn)
                        else -> l.copy(invoiceId = id)
                    }
                },
            )
        }
        return id
    }

    override suspend fun voidInvoice(id: Long, on: LocalDate) {
        data.update { d ->
            val invoice = d.invoices[id] ?: return@update d
            d.copy(
                invoices = d.invoices + (id to invoice.copy(voidedOn = invoice.voidedOn ?: on)),
                lessons = d.lessons.mapValues { (_, l) -> if (l.invoiceId == id) l.copy(invoiceId = null) else l },
            )
        }
    }

    override suspend fun setDocument(id: Long, document: InvoiceDocument) {
        data.update { d ->
            val invoice = d.invoices[id]
            if (invoice == null || invoice.document != null) {
                d
            } else {
                d.copy(invoices = d.invoices + (id to invoice.copy(document = document)))
            }
        }
    }

    // Backup

    override suspend fun readAll(): BackupSnapshot = BackupSnapshot(
        exportedAt = "2026-01-01T00:00:00Z",
        settings = settingsRepository.settings.value,
        students = students,
        groups = groups,
        lessons = lessons,
        invoices = invoices,
    )

    override suspend fun replaceAll(snapshot: BackupSnapshot) {
        val maxId = (snapshot.students.map { it.id } + snapshot.groups.map { it.id } +
            snapshot.lessons.map { it.id } + snapshot.invoices.map { it.id }).maxOrNull() ?: 0
        data.value = Data(
            students = snapshot.students.associateBy { it.id },
            groups = snapshot.groups.associateBy { it.id },
            lessons = snapshot.lessons.associateBy { it.id },
            invoices = snapshot.invoices.associateBy { it.id },
            nextId = maxId + 1,
        )
        settingsRepository.update { snapshot.settings }
    }
}

class InMemorySettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.update(transform)
    }
}
