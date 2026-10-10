package gr.eduinvoice.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.eduinvoice.core.model.DomainException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Launches [block] and turns failures into a user-facing message instead of a crash. */
fun ViewModel.launchCatching(onError: (String) -> Unit, block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException) {
            onError(e.message ?: "Not allowed")
        } catch (e: Exception) {
            onError("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
        }
    }

fun plural(count: Int, word: String): String = "$count $word" + if (count == 1) "" else "s"
