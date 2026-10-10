package gr.eduinvoice

import android.content.Context
import gr.eduinvoice.core.backup.BackupRepository
import gr.eduinvoice.core.repository.GroupRepository
import gr.eduinvoice.core.repository.InvoiceRepository
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.SettingsRepository
import gr.eduinvoice.core.repository.StudentRepository
import gr.eduinvoice.data.BackupFiles
import gr.eduinvoice.data.EduDatabase
import gr.eduinvoice.data.InvoiceExporter
import gr.eduinvoice.data.InvoicePdfWriter
import gr.eduinvoice.data.PrefsSettingsRepository
import gr.eduinvoice.data.SqlBackupRepository
import gr.eduinvoice.data.SqlGroupRepository
import gr.eduinvoice.data.SqlInvoiceRepository
import gr.eduinvoice.data.SqlLessonRepository
import gr.eduinvoice.data.SqlStudentRepository

/** Manual constructor injection: the single place where the object graph is wired. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = EduDatabase(appContext)

    val settingsRepository: SettingsRepository = PrefsSettingsRepository(appContext)
    val studentRepository: StudentRepository = SqlStudentRepository(database)
    val groupRepository: GroupRepository = SqlGroupRepository(database)
    val lessonRepository: LessonRepository = SqlLessonRepository(database)
    val invoiceRepository: InvoiceRepository = SqlInvoiceRepository(database)
    val backupRepository: BackupRepository = SqlBackupRepository(database, settingsRepository)

    val backupFiles = BackupFiles(appContext, backupRepository)
    val invoiceExporter = InvoiceExporter(
        students = studentRepository,
        groups = groupRepository,
        lessons = lessonRepository,
        invoices = invoiceRepository,
        settings = settingsRepository,
        pdfWriter = InvoicePdfWriter(appContext),
    )
}
