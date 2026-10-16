package gr.eduinvoice.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Tables whose changes are broadcast to observers. */
enum class Table { STUDENTS, GROUPS, LESSONS, INVOICES }

/**
 * Framework SQLite database (no Room, see docs/PRODUCT_SPEC.md D2). Reads and writes run on [io];
 * every committed write bumps the version of the tables it touched so `observe` flows re-query.
 */
class EduDatabase(
    context: Context,
    name: String? = NAME,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SQLiteOpenHelper(context, name, null, VERSION) {
    private val versions = Table.entries.associateWith { MutableStateFlow(0L) }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        Schema.statements.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Each step upgrades exactly one version; never drop user data.
        if (oldVersion < 2) Schema.migrateTo2.forEach(db::execSQL)
        if (oldVersion < 3) Schema.migrateTo3.forEach(db::execSQL)
    }

    fun <T> observe(vararg tables: Table, query: (SQLiteDatabase) -> T): Flow<T> =
        combine(tables.map { versions.getValue(it) }) { it.sum() }
            .conflate()
            .map { query(readableDatabase) }
            .flowOn(io)

    suspend fun <T> read(query: (SQLiteDatabase) -> T): T = withContext(io) { query(readableDatabase) }

    /**
     * Runs [block] in a transaction, then notifies observers of [tables]. Once started, a write always
     * finishes and notifies, even if the caller is cancelled meanwhile; otherwise a committed change could
     * stay invisible to open screens.
     */
    suspend fun <T> write(vararg tables: Table, block: (SQLiteDatabase) -> T): T =
        withContext(NonCancellable + io) {
            val db = writableDatabase
            db.beginTransaction()
            val result = try {
                block(db).also { db.setTransactionSuccessful() }
            } finally {
                db.endTransaction()
            }
            tables.forEach { table -> versions.getValue(table).update { it + 1 } }
            result
        }

    companion object {
        const val NAME = "eduinvoice_v2.db"
        /**
         * 1: v2 baseline. 2: invoices.voided_on (invoices are voided, never deleted).
         * 3: invoices.document (the content as issued, R12/D10).
         */
        const val VERSION = 3

        /** SQLite on API 26 allows at most 999 bound arguments per statement. */
        const val MAX_ARGS = 500
    }
}

internal object Schema {
    val statements = listOf(
        """
        CREATE TABLE students (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            first_name TEXT NOT NULL,
            last_name TEXT NOT NULL DEFAULT '',
            class_name TEXT NOT NULL DEFAULT '',
            guardian_name TEXT NOT NULL DEFAULT '',
            phone TEXT NOT NULL DEFAULT '',
            email TEXT NOT NULL DEFAULT '',
            rate_cents INTEGER NOT NULL CHECK (rate_cents >= 0),
            rate_type TEXT NOT NULL CHECK (rate_type IN ('HOURLY', 'PER_LESSON')),
            notes TEXT NOT NULL DEFAULT '',
            archived INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE student_groups (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            class_name TEXT NOT NULL DEFAULT '',
            rate_cents INTEGER NOT NULL CHECK (rate_cents >= 0),
            rate_type TEXT NOT NULL CHECK (rate_type IN ('HOURLY', 'PER_LESSON')),
            archived INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE group_members (
            group_id INTEGER NOT NULL REFERENCES student_groups (id) ON DELETE CASCADE,
            student_id INTEGER NOT NULL REFERENCES students (id) ON DELETE CASCADE,
            PRIMARY KEY (group_id, student_id)
        )
        """,
        "CREATE INDEX idx_group_members_student ON group_members (student_id)",
        """
        CREATE TABLE invoices (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            number TEXT NOT NULL UNIQUE,
            student_id INTEGER NOT NULL REFERENCES students (id) ON DELETE RESTRICT,
            issue_date TEXT NOT NULL,
            total_cents INTEGER NOT NULL,
            notes TEXT NOT NULL DEFAULT '',
            voided_on TEXT,
            document TEXT
        )
        """,
        "CREATE INDEX idx_invoices_student ON invoices (student_id)",
        """
        CREATE TABLE lessons (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            student_id INTEGER NOT NULL REFERENCES students (id) ON DELETE CASCADE,
            group_id INTEGER REFERENCES student_groups (id) ON DELETE SET NULL,
            date TEXT NOT NULL,
            start_time TEXT NOT NULL,
            duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
            fee_cents INTEGER NOT NULL CHECK (fee_cents >= 0),
            absent INTEGER NOT NULL DEFAULT 0,
            notes TEXT NOT NULL DEFAULT '',
            paid_on TEXT,
            invoice_id INTEGER REFERENCES invoices (id) ON DELETE SET NULL
        )
        """,
        "CREATE INDEX idx_lessons_student ON lessons (student_id)",
        "CREATE INDEX idx_lessons_date ON lessons (date)",
        "CREATE INDEX idx_lessons_group ON lessons (group_id)",
        "CREATE INDEX idx_lessons_invoice ON lessons (invoice_id)",
    ).map { it.trimIndent() }

    val migrateTo2 = listOf("ALTER TABLE invoices ADD COLUMN voided_on TEXT")

    /** Existing invoices get no document; it is frozen the first time each one is shared. */
    val migrateTo3 = listOf("ALTER TABLE invoices ADD COLUMN document TEXT")
}

internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

internal fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

internal fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

internal fun Cursor.bool(column: String): Boolean = getInt(getColumnIndexOrThrow(column)) != 0

internal fun Cursor.longOrNull(column: String): Long? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getLong(it) }

internal fun Cursor.stringOrNull(column: String): String? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

internal inline fun <T> Cursor.readAll(row: (Cursor) -> T): List<T> = use { cursor ->
    buildList { while (cursor.moveToNext()) add(row(cursor)) }
}

internal fun SQLiteDatabase.select(sql: String, vararg args: Any): Cursor =
    rawQuery(sql, args.map { it.toString() }.toTypedArray())

/** "?, ?, ?" for an IN clause. */
internal fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")
