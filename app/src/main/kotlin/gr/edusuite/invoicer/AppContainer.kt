package gr.edusuite.invoicer

import android.content.Context
import gr.edusuite.invoicer.core.backup.BackupRepository
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.ReminderStateRepository
import gr.edusuite.invoicer.core.repository.SettingsRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import gr.edusuite.invoicer.core.usecase.GetInvoiceDocument
import gr.edusuite.invoicer.core.usecase.InvoiceRenderer
import gr.edusuite.invoicer.data.BackupFiles
import gr.edusuite.invoicer.data.DatabaseHealth
import gr.edusuite.invoicer.data.InvoiceExporter
import gr.edusuite.invoicer.data.InvoicePdfWriter
import gr.edusuite.invoicer.data.InvoicerDatabase
import gr.edusuite.invoicer.data.PrefsReminderStateRepository
import gr.edusuite.invoicer.data.PrefsSettingsRepository
import gr.edusuite.invoicer.data.SqlBackupRepository
import gr.edusuite.invoicer.data.SqlGroupRepository
import gr.edusuite.invoicer.data.SqlInvoiceRepository
import gr.edusuite.invoicer.data.SqlLessonRepository
import gr.edusuite.invoicer.data.SqlStudentRepository
import gr.edusuite.invoicer.ui.common.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manual constructor injection: the single place where the object graph is wired. Instrumented tests pass
 * their own [databaseName] (null = in memory), [settingsFile] and [reminderStateFile] so they never touch the
 * tutor's data.
 */
class AppContainer(
    context: Context,
    databaseName: String? = InvoicerDatabase.NAME,
    settingsFile: String = PrefsSettingsRepository.FILE_NAME,
    reminderStateFile: String = PrefsReminderStateRepository.FILE_NAME,
) {
    val messages = Messages()
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val appContext = context.applicationContext
    val databaseHealth = DatabaseHealth(appContext, if (databaseName == null) "$settingsFile.database_health" else "database_health")
    private val database = InvoicerDatabase(appContext, databaseName, health = databaseHealth)
    private val startupState = MutableStateFlow(DatabaseStartup())
    val startup = startupState.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            startupState.value = try {
                database.read { Unit }
                DatabaseStartup(ready = true)
            } catch (error: Exception) {
                DatabaseStartup(error = error.message ?: "The database could not be opened")
            }
        }
    }

    val settingsRepository: SettingsRepository = PrefsSettingsRepository(appContext, settingsFile)
    val studentRepository: StudentRepository = SqlStudentRepository(database)
    val groupRepository: GroupRepository = SqlGroupRepository(database)
    val lessonRepository: LessonRepository = SqlLessonRepository(database)
    val invoiceRepository: InvoiceRepository = SqlInvoiceRepository(database)
    val backupRepository: BackupRepository = SqlBackupRepository(database, settingsRepository)
    val reminderStateRepository: ReminderStateRepository = PrefsReminderStateRepository(appContext, reminderStateFile)

    val backupFiles = BackupFiles(appContext, backupRepository)
    val invoiceRenderer = InvoiceRenderer(studentRepository, groupRepository, settingsRepository)
    val invoiceExporter = InvoiceExporter(
        getDocument = GetInvoiceDocument(lessonRepository, invoiceRepository, invoiceRenderer),
        pdfWriter = InvoicePdfWriter(appContext),
    )
}

/** Navigation starts only after the database has opened safely. */
data class DatabaseStartup(val ready: Boolean = false, val error: String? = null)
