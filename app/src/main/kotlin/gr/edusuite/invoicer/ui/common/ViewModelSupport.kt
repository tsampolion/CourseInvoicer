package gr.edusuite.invoicer.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.edusuite.invoicer.core.model.DomainException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/** Launches [block] and turns failures into a user-facing message instead of a crash. */
fun ViewModel.launchCatching(onError: (String) -> Unit, block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(messageFor(e))
        }
    }

fun plural(count: Int, word: String): String = "$count $word" + if (count == 1) "" else "s"

private fun messageFor(e: Throwable): String = when (e) {
    is DomainException -> e.message ?: "Not allowed"
    else -> "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
}

/** Keeps the last good state on an upstream error and reports it. Cancellation still propagates. */
fun <T> Flow<T>.stateInCatching(
    scope: CoroutineScope,
    initial: T,
    onError: (String) -> Unit,
): StateFlow<T> = catch { error ->
    if (error is CancellationException) throw error
    onError(messageFor(error))
}.stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial)
