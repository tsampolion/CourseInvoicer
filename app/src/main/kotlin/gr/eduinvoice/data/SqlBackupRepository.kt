package gr.eduinvoice.data

import gr.eduinvoice.core.backup.BackupRepository
import gr.eduinvoice.core.backup.BackupSnapshot
import gr.eduinvoice.core.repository.SettingsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant

class SqlBackupRepository(
    private val db: EduDatabase,
    private val settings: SettingsRepository,
) : BackupRepository {
    override suspend fun readAll(): BackupSnapshot = db.read { d ->
        BackupSnapshot(
            exportedAt = Instant.now().toString(),
            settings = settings.settings.value,
            students = d.allStudents(),
            groups = d.allGroups(),
            lessons = d.lessons("1 = 1"),
            invoices = d.allInvoices(),
        )
    }

    /**
     * Replaces everything in one transaction; a broken backup leaves the current data untouched. Once the
     * data is replaced, the backup's settings are applied too, even if the caller is cancelled (R11).
     */
    override suspend fun replaceAll(snapshot: BackupSnapshot) = withContext(NonCancellable) {
        db.write(*Table.entries.toTypedArray()) { d ->
            listOf("lessons", "invoices", "group_members", "student_groups", "students").forEach { table ->
                d.delete(table, null, null)
            }
            snapshot.students.forEach { d.insertOrThrow("students", null, it.toValues(includeId = true)) }
            snapshot.groups.forEach { group ->
                d.insertOrThrow("student_groups", null, group.toValues(includeId = true))
                group.memberIds.forEach { studentId -> d.insertMember(group.id, studentId) }
            }
            snapshot.invoices.forEach { d.insertOrThrow("invoices", null, it.toValues(includeId = true)) }
            snapshot.lessons.forEach { d.insertOrThrow("lessons", null, it.toValues(includeId = true)) }
        }
        settings.update { snapshot.settings }
    }
}
