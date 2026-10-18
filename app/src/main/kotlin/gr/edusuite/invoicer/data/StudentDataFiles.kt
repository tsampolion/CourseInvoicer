package gr.edusuite.invoicer.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Writes one student's data export (GDPR Art. 15, 20; `studentDataJson`) to the app cache and returns a
 * content:// Uri for the share sheet. Exports older than a day are deleted first, so personal data does not
 * pile up in the cache.
 */
class StudentDataFiles(private val context: Context) {
    suspend fun write(fileStem: String, json: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val safe = fileStem.lowercase().replace(Regex("[^\\p{L}\\p{N}_-]+"), "-").trim('-').ifEmpty { "student" }
        val file = File(dir, "$safe-data.json")
        file.writeText(json)
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private companion object {
        /** Matches the `exports/` cache path shared through the FileProvider (res/xml/file_paths.xml). */
        const val DIRECTORY = "exports"
    }
}
