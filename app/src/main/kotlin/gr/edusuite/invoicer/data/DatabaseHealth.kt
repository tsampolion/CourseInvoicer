package gr.edusuite.invoicer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-local recovery warning. Excluded from cloud backup with all other preferences. */
class DatabaseHealth(context: Context, fileName: String = "database_health") {
    private val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getLong("damaged_at", 0L) != 0L)
    val damaged: StateFlow<Boolean> = state.asStateFlow()

    fun recordDamage() {
        check(prefs.edit().putLong("damaged_at", System.currentTimeMillis()).commit()) {
            "Could not record the damaged database warning"
        }
        state.value = true
    }

    fun acknowledge() {
        check(prefs.edit().remove("damaged_at").commit()) { "Could not dismiss the database warning" }
        state.value = false
    }
}
