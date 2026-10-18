package gr.edusuite.invoicer.ui.common

import androidx.lifecycle.SavedStateHandle

/** Bundle-compatible fields only; the handle belongs to one navigation entry. */
internal class FormDraft(private val handle: SavedStateHandle) {
    val exists: Boolean get() = handle.get<Boolean>("draft.present") == true
    fun text(key: String, default: String = ""): String = handle["draft.$key"] ?: default
    fun number(key: String, default: Long = 0): Long = handle["draft.$key"] ?: default
    fun flag(key: String, default: Boolean = false): Boolean = handle["draft.$key"] ?: default
    fun ids(key: String): Set<Long> = handle.get<LongArray>("draft.$key")?.toSet().orEmpty()

    fun save(vararg fields: Pair<String, Any?>) {
        fields.forEach { (key, value) -> handle["draft.$key"] = value }
        handle["draft.present"] = true
    }

    fun clear() {
        handle.keys().filter { it.startsWith("draft.") }.forEach { handle.remove<Any?>(it) }
    }
}
