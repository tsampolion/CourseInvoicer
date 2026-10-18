package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.model.MAX_RATE
import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.privacy.ERASED_STUDENT
import gr.edusuite.invoicer.core.privacy.anonymised
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import kotlinx.coroutines.flow.first

class SaveStudent(private val students: StudentRepository) {
    suspend operator fun invoke(student: Student): Long {
        if (student.firstName.isBlank()) throw DomainException("First name is required")
        if (student.rate.amount.cents < 0) throw DomainException("Rate cannot be negative")
        if (student.rate.amount > MAX_RATE) throw DomainException("Rate cannot be more than 100,000")
        if (student.email.isNotBlank() && !EMAIL.matches(student.email.trim())) {
            throw DomainException("Email address is not valid")
        }
        return students.saveStudent(
            student.copy(
                firstName = student.firstName.trim(),
                lastName = student.lastName.trim(),
                className = student.className.trim(),
                guardianName = student.guardianName.trim(),
                phone = student.phone.trim(),
                email = student.email.trim(),
                notes = student.notes.trim(),
            ),
        )
    }

    private companion object {
        val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    }
}

/** A student can be deleted only if nothing refers to them: no lessons, and no invoices (kept forever, R6). */
class DeleteStudent(
    private val students: StudentRepository,
    private val lessons: LessonRepository,
    private val invoices: InvoiceRepository,
) {
    suspend operator fun invoke(studentId: Long) {
        if (lessons.countLessonsOfStudent(studentId) > 0) {
            throw DomainException("This student has lessons. Archive them instead to keep the history.")
        }
        if (invoices.countInvoicesOfStudent(studentId) > 0) {
            throw DomainException("This student has statements, which are kept for good. Archive them instead.")
        }
        students.deleteStudent(studentId)
    }
}

/** Archives or restores a student without changing its history (R10). */
class ArchiveStudent(private val students: StudentRepository) {
    suspend operator fun invoke(studentId: Long, archived: Boolean): Undoable<Unit> {
        val existing = students.getStudent(studentId)
            ?: throw DomainException("This student no longer exists")
        if (existing.archived == archived) return Undoable(Unit, Undo {})
        students.saveStudent(existing.copy(archived = archived))
        return Undoable(Unit, undoOnce {
            val current = students.getStudent(studentId)
                ?: throw DomainException("This student no longer exists")
            students.saveStudent(current.copy(archived = existing.archived))
        })
    }
}

/**
 * Erases a student's personal data by anonymising it (R17), for when lessons have ended and everything is
 * paid. The student becomes [ERASED_STUDENT] with no contact details or notes and is archived; their
 * lessons lose their notes and absence notes; each of their issued statements keeps its number, dates and
 * amounts but bills [ERASED_STUDENT] (the one permitted change to an issued document). It is one
 * transaction. The undo puts back what was erased onto the current rows, so a payment recorded in the
 * meantime stays.
 */
class EraseStudentData(
    private val students: StudentRepository,
    private val lessons: LessonRepository,
    private val invoices: InvoiceRepository,
) {
    suspend operator fun invoke(studentId: Long): Undoable<Unit> {
        val student = students.getStudent(studentId) ?: throw DomainException("This student no longer exists")
        val theirLessons = lessons.observeLessonsOfStudent(studentId).first()
        val theirInvoices = invoices.observeInvoices().first().filter { it.studentId == studentId }
        students.rewriteStudentData(
            student.anonymised(),
            theirLessons.map { it.copy(notes = "", absenceNote = "") },
            theirInvoices.map { it.copy(notes = "", document = it.document?.anonymised()) },
        )
        return Undoable(Unit, undoOnce {
            val current = students.getStudent(studentId) ?: throw DomainException("This student no longer exists")
            students.rewriteStudentData(
                student.copy(rate = current.rate),
                theirLessons,
                theirInvoices,
            )
        })
    }
}
