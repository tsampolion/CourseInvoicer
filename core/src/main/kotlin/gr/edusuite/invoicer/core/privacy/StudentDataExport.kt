package gr.edusuite.invoicer.core.privacy

import gr.edusuite.invoicer.core.backup.BackupCodec
import gr.edusuite.invoicer.core.backup.Json
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Student

/** Marks a file from [studentDataJson]; it is not a backup and cannot be imported. */
const val STUDENT_DATA_FORMAT = "course-invoicer-student-data"

/**
 * One student's data as JSON, for a parent's request for access or portability (GDPR Art. 15, 20): the
 * student, their lessons and their statements, in the backup's field names (ISO dates, money in cents).
 * Only [student]'s own lessons and statements are written, whatever the lists hold.
 */
fun studentDataJson(student: Student, lessons: List<Lesson>, invoices: List<Invoice>, exportedAt: String): String =
    with(BackupCodec) {
        Json.write(
            mapOf(
                "format" to STUDENT_DATA_FORMAT,
                "version" to 1L,
                "exportedAt" to exportedAt,
                "student" to student.toJson(),
                "lessons" to lessons.filter { it.studentId == student.id }.map { it.toJson() },
                "statements" to invoices.filter { it.studentId == student.id }.map { it.toJson() },
            ),
        )
    }
