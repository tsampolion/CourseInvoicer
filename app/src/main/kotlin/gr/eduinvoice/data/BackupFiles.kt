package gr.eduinvoice.data

import android.content.Context
import android.net.Uri
import gr.eduinvoice.core.backup.BackupCodec
import gr.eduinvoice.core.backup.BackupFormatException
import gr.eduinvoice.core.backup.BackupRepository
import gr.eduinvoice.core.backup.BackupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Reads and writes backup files picked through the Storage Access Framework. */
class BackupFiles(
    private val context: Context,
    private val backups: BackupRepository,
) {
    suspend fun export(uri: Uri) {
        val json = BackupCodec.encode(backups.readAll())
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Cannot write to the selected file")
            stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }
    }

    /** Validates the whole file before replacing any data. */
    suspend fun import(uri: Uri): BackupSnapshot {
        val text = withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot read the selected file")
            stream.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) throw BackupFormatException("The file is too large to be a backup")
                bytes.toString(Charsets.UTF_8)
            }
        }
        val snapshot = withContext(Dispatchers.Default) { BackupCodec.decode(text) }
        backups.replaceAll(snapshot)
        return snapshot
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (buffer.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 50 * 1024 * 1024
    }
}
