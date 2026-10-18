package gr.edusuite.invoicer.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.core.usecase.Undoable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class UserMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (suspend () -> Unit)? = null,
)

class Messages {
    private val pending = MutableSharedFlow<UserMessage>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val flow: SharedFlow<UserMessage> = pending.asSharedFlow()

    fun post(message: UserMessage) { pending.tryEmit(message) }

    fun <T> postUndo(text: String, action: Undoable<T>) = post(UserMessage(text, "Undo") { action.undo() })
}

/** Latest offer wins; its action runs outside the collector so navigation cannot cancel it. */
internal suspend fun Messages.showIn(
    host: SnackbarHostState,
    applicationScope: CoroutineScope,
) {
    flow.collectLatest { message ->
        val result = withTimeoutOrNull(UNDO_WINDOW_MILLIS) {
            host.showSnackbar(message.text, message.actionLabel, duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) {
            applicationScope.launch {
                try {
                    message.action?.invoke()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val text = if (error is DomainException) error.message ?: "Not allowed"
                        else "Something went wrong: ${error.message ?: error.javaClass.simpleName}"
                    post(UserMessage(text))
                }
            }
        }
    }
}
