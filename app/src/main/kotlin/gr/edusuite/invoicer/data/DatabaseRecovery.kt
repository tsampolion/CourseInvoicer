package gr.edusuite.invoicer.data

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.File

class DatabaseTooNewException(old: Int, new: Int) : SQLiteException(
    "This build is older than your data (database version $old, this build understands $new). Install the latest release.",
)

/** Keep the original before allowing the framework to reset a damaged database. */
internal class KeepingDatabaseErrorHandler(
    private val context: Context,
    private val name: String?,
    private val health: DatabaseHealth,
) : DatabaseErrorHandler {
    override fun onCorruption(db: SQLiteDatabase) {
        if (name != null) {
            try {
                val source = context.getDatabasePath(name)
                source.copyTo(File(source.parentFile, "$name.bad"), overwrite = true)
                // Keep any journal alongside the main file; it can contain committed data.
                for (suffix in listOf("-wal", "-shm", "-journal")) {
                    val sidecar = File(source.path + suffix)
                    if (sidecar.exists()) sidecar.copyTo(File(source.path + ".bad" + suffix), overwrite = true)
                }
                health.recordDamage()
            } catch (error: Exception) {
                Log.e("CourseInvoicer", "Could not preserve the damaged database; refusing to reset it", error)
                // Never delegate to a deleting handler unless preservation succeeded.
                throw SQLiteException("The database is damaged and could not be copied. Your original file was kept.", error)
            }
        }
        DefaultDatabaseErrorHandler().onCorruption(db)
    }
}
