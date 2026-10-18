package gr.edusuite.invoicer.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import gr.edusuite.invoicer.AppContainer
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Rate
import gr.edusuite.invoicer.core.model.RateType
import gr.edusuite.invoicer.core.model.Student
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class InvoicerDatabaseTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun corruptDatabaseIsPreservedAndRecreatedWithAPersistentWarning() {
        val name = "corruption-${UUID.randomUUID()}.db"
        val prefs = "$name.health"
        val health = DatabaseHealth(context, prefs)
        val file = context.getDatabasePath(name)
        val garbage = "not a database".toByteArray() + ByteArray(4096)
        try {
            InvoicerDatabase(context, name, health = health).use { it.writableDatabase }
            file.writeBytes(garbage)
            InvoicerDatabase(context, name, health = health).use { helper ->
                helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM students", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
            assertArrayEquals(garbage, File(file.path + ".bad").readBytes())
            assertTrue(health.damaged.value)
            val restarted = DatabaseHealth(context, prefs)
            assertTrue(restarted.damaged.value)
            restarted.acknowledge()
            assertFalse(DatabaseHealth(context, prefs).damaged.value)
        } finally { cleanup(name, prefs) }
    }

    @Test fun aFailedPreservationNeverDeletesTheOriginal() {
        val name = "copy-failure-${UUID.randomUUID()}.db"
        val prefs = "$name.health"
        val file = context.getDatabasePath(name)
        val original = "keep these bytes".toByteArray()
        try {
            file.parentFile!!.mkdirs()
            file.writeBytes(original)
            File(file.path + ".bad").mkdir()
            SQLiteDatabase.create(null).use { memory ->
                val error = assertThrows(SQLiteException::class.java) {
                    KeepingDatabaseErrorHandler(context, name, DatabaseHealth(context, prefs)).onCorruption(memory)
                }
                assertTrue(error.message!!.contains("original file was kept"))
                assertArrayEquals(original, file.readBytes())
                assertTrue(memory.isOpen)
            }
        } finally { cleanup(name, prefs) }
    }

    @Test fun downgradeRefusesNewerDataAndKeepsItsRows() = runBlocking {
        val name = "downgrade-${UUID.randomUUID()}.db"
        val prefs = "$name.health"
        try {
            InvoicerDatabase(context, name, health = DatabaseHealth(context, prefs)).use { helper ->
                SqlStudentRepository(helper).saveStudent(Student(firstName = "Keep me", rate = Rate(Money(2000), RateType.HOURLY)))
                helper.writableDatabase.version = InvoicerDatabase.VERSION + 1
            }
            InvoicerDatabase(context, name, health = DatabaseHealth(context, prefs)).use { helper ->
                val error = assertThrows(DatabaseTooNewException::class.java) { helper.readableDatabase }
                assertEquals(
                    "This build is older than your data (database version 5, this build understands 4). Install the latest release.",
                    error.message,
                )
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
                assertEquals(InvoicerDatabase.VERSION + 1, raw.version)
                raw.rawQuery("SELECT first_name FROM students", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Keep me", cursor.getString(0))
                }
            }
        } finally { cleanup(name, prefs) }
    }

    @Test fun startupGuardReportsNewerDataBeforeNavigationStarts() = runBlocking {
        val name = "startup-${UUID.randomUUID()}.db"
        val settings = "$name.settings"
        val reminders = "$name.reminders"
        try {
            InvoicerDatabase(context, name).use { it.writableDatabase.version = InvoicerDatabase.VERSION + 1 }
            val container = AppContainer(context, name, settings, reminders)
            val startup = withTimeout(10_000) { container.startup.first { it.error != null || it.ready } }
            assertFalse(startup.ready)
            assertTrue(startup.error!!.contains("This build is older than your data"))
            assertTrue(context.getDatabasePath(name).exists())
        } finally {
            cleanup(name, "$name.health")
            context.deleteSharedPreferences(settings)
            context.deleteSharedPreferences(reminders)
        }
    }

    private fun cleanup(name: String, prefs: String) {
        context.deleteDatabase(name)
        for (suffix in listOf(".bad", ".bad-wal", ".bad-shm", ".bad-journal")) {
            File(context.getDatabasePath(name).path + suffix).deleteRecursively()
        }
        context.deleteSharedPreferences(prefs)
    }
}
