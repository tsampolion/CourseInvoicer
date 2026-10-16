package gr.edusuite.invoicer

import android.content.Context
import gr.edusuite.invoicer.core.backup.BackupRepository
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.SettingsRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import gr.edusuite.invoicer.core.usecase.GetInvoiceDocument
import gr.edusuite.invoicer.core.usecase.InvoiceRenderer
import gr.edusuite.invoicer.data.BackupFiles
import gr.edusuite.invoicer.data.InvoicerDatabase
import gr.edusuite.invoicer.data.InvoiceExporter
import gr.edusuite.invoicer.data.InvoicePdfWriter
import gr.edusuite.invoicer.data.PrefsSettingsRepository
import gr.edusuite.invoicer.data.SqlBackupRepository
import gr.edusuite.invoicer.data.SqlGroupRepository
import gr.edusuite.invoicer.data.SqlInvoiceRepository
import gr.edusuite.invoicer.data.SqlLessonRepository
import gr.edusuite.invoicer.data.SqlStudentRepository

/**
 * Manual constructor injection: the single place where the object graph is wired. Instrumented tests pass
 * their own [databaseName] (null = in memory) and [settingsFile] so they never touch the tutor's data.
 */
class AppContainer(
    context: Context,
    databaseName: String? = InvoicerDatabase.NAME,
    settingsFile: String = PrefsSettingsRepository.FILE_NAME,
) {
    private val appContext = context.applicationContext
    private val database = InvoicerDatabase(appContext, databaseName)

    val settingsRepository: SettingsRepository = PrefsSettingsRepository(appContext, settingsFile)
    val studentRepository: StudentRepository = SqlStudentRepository(database)
    val groupRepository: GroupRepository = SqlGroupRepository(database)
    val lessonRepository: LessonRepository = SqlLessonRepository(database)
    val invoiceRepository: InvoiceRepository = SqlInvoiceRepository(database)
    val backupRepository: BackupRepository = SqlBackupRepository(database, settingsRepository)

    val backupFiles = BackupFiles(appContext, backupRepository)
    val invoiceRenderer = InvoiceRenderer(studentRepository, groupRepository, settingsRepository)
    val invoiceExporter = InvoiceExporter(
        getDocument = GetInvoiceDocument(lessonRepository, invoiceRepository, invoiceRenderer),
        pdfWriter = InvoicePdfWriter(appContext),
    )
}
