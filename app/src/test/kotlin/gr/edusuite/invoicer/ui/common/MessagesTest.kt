package gr.edusuite.invoicer.ui.common

import androidx.compose.material3.SnackbarHostState
import gr.edusuite.invoicer.core.model.DomainException
import gr.edusuite.invoicer.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test fun `offer expires at five seconds and never runs on timeout`() = runTest {
        val messages = Messages()
        val host = SnackbarHostState()
        var invoked = false
        backgroundScope.launch { messages.showIn(host, backgroundScope) }
        runCurrent()
        messages.post(UserMessage("Deleted", "Undo") { invoked = true })
        runCurrent()
        advanceTimeBy(4_999)
        assertEquals("Undo", host.currentSnackbarData?.visuals?.actionLabel)
        advanceTimeBy(1)
        runCurrent()
        assertNull(host.currentSnackbarData)
        assertFalse(invoked)
    }

    @Test fun `new message replaces the visible offer and errors become plain messages`() = runTest {
        val messages = Messages()
        val host = SnackbarHostState()
        var olderInvoked = false
        backgroundScope.launch { messages.showIn(host, backgroundScope) }
        runCurrent()
        messages.post(UserMessage("Older", "Undo") { olderInvoked = true })
        runCurrent()
        messages.post(UserMessage("Newer", "Undo") { throw DomainException("Now locked") })
        runCurrent()
        assertEquals("Newer", host.currentSnackbarData?.visuals?.message)
        host.currentSnackbarData!!.performAction()
        runCurrent()
        assertFalse(olderInvoked)
        assertEquals("Now locked", host.currentSnackbarData?.visuals?.message)
        assertNull(host.currentSnackbarData?.visuals?.actionLabel)
    }

    @Test fun `an accepted undo survives cancellation of the screen collector`() = runTest {
        val messages = Messages()
        val host = SnackbarHostState()
        var restored = false
        val collector = backgroundScope.launch { messages.showIn(host, backgroundScope) }
        runCurrent()
        messages.post(UserMessage("Deleted", "Undo") { delay(100); restored = true })
        runCurrent()
        host.currentSnackbarData!!.performAction()
        runCurrent()
        collector.cancel()
        advanceTimeBy(100)
        runCurrent()
        assertTrue(restored)
    }
}
