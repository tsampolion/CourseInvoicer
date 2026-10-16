package gr.edusuite.invoicer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import gr.edusuite.invoicer.core.model.ReminderState
import gr.edusuite.invoicer.core.repository.ReminderStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * The month-end reminder's answers in their own SharedPreferences file, apart from the settings so a backup
 * never carries them (R15). Like every app file it is kept out of cloud backup (`data_extraction_rules.xml`).
 */
class PrefsReminderStateRepository(context: Context, fileName: String = FILE_NAME) : ReminderStateRepository {
    private val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(prefs.readState())
    private val writes = Mutex()

    override val state: StateFlow<ReminderState> = mutableState.asStateFlow()

    override suspend fun update(transform: (ReminderState) -> ReminderState) {
        writes.withLock {
            withContext(NonCancellable + Dispatchers.IO) {
                val updated = transform(mutableState.value)
                prefs.writeState(updated)
                mutableState.value = updated
            }
        }
    }

    // A value that does not parse is treated as never set: the worst case is one reminder too many.
    private fun SharedPreferences.readState() = ReminderState(
        doneFor = getString(DONE_FOR, null)?.let { parseOrNull { YearMonth.parse(it) } },
        snoozedUntil = getString(SNOOZED_UNTIL, null)?.let { parseOrNull { LocalDate.parse(it) } },
    )

    private fun SharedPreferences.writeState(answers: ReminderState) {
        edit(commit = true) {
            // "2026-03" and "2026-03-30", the ISO forms YearMonth and LocalDate print and parse. Null removes the key.
            putString(DONE_FOR, answers.doneFor?.toString())
            putString(SNOOZED_UNTIL, answers.snoozedUntil?.toString())
        }
    }

    private inline fun <T> parseOrNull(parse: () -> T): T? = try {
        parse()
    } catch (_: DateTimeParseException) {
        null
    }

    companion object {
        const val FILE_NAME = "reminder_state"

        private const val DONE_FOR = "done_for"
        private const val SNOOZED_UNTIL = "snoozed_until"
    }
}
