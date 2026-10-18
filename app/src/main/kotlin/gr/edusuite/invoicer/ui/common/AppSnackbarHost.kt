package gr.edusuite.invoicer.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

const val UNDO_WINDOW_MILLIS = 5_000L

/** The message identity restarts the countdown when a newer action replaces it. */
@Composable
fun AppSnackbarHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = host, modifier = modifier) { data ->
        val remaining = remember(data) { Animatable(1f) }
        val animate = animationsEnabled()
        LaunchedEffect(data, animate) {
            if (animate) remaining.animateTo(0f, tween(UNDO_WINDOW_MILLIS.toInt(), easing = LinearEasing))
        }
        Column {
            Snackbar(data)
            if (animate && data.visuals.actionLabel != null) {
                LinearProgressIndicator(
                    progress = { remaining.value },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
        }
    }
}
