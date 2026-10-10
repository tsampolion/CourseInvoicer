package gr.eduinvoice.core.usecase

import gr.eduinvoice.core.model.DomainException
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.StudentRepository

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

class DeleteStudent(
    private val students: StudentRepository,
    private val lessons: LessonRepository,
) {
    suspend operator fun invoke(studentId: Long) {
        if (lessons.countLessonsOfStudent(studentId) > 0) {
            throw DomainException("This student has lessons. Archive them instead to keep the history.")
        }
        students.deleteStudent(studentId)
    }
}
