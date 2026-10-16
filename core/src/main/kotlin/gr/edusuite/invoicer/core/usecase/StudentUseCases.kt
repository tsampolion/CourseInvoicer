package gr.edusuite.invoicer.core.usecase

import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.StudentRepository

class SaveStudent(private val students: StudentRepository) {
    suspend operator fun invoke(student: Student): Long {
        if (student.firstName.isBlank()) throw DomainException("First name is required")
        if (student.rate.amount.cents < 0) throw DomainException("Rate cannot be negative")
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
            throw DomainException("This student has invoices, which are kept for good. Archive them instead.")
        }
        students.deleteStudent(studentId)
    }
}
