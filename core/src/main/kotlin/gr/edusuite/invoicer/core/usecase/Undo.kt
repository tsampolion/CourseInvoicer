package gr.edusuite.invoicer.core.usecase

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reverses exactly what one action changed. Running it twice is harmless. */
fun interface Undo { suspend operator fun invoke() }

/** The original result and the way back. */
data class Undoable<T>(val result: T, val undo: Undo)

/** Only a successful undo is consumed; concurrent or repeated calls cannot repeat a write. */
internal fun undoOnce(block: suspend () -> Unit): Undo {
    val mutex = Mutex()
    var completed = false
    return Undo {
        mutex.withLock {
            if (!completed) {
                withContext(NonCancellable) {
                    block()
                    completed = true
                }
            }
        }
    }
}
