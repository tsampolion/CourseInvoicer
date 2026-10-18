package gr.edusuite.invoicer.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** The system and toolbar Back buttons share one prompt. Saved actions still close normally. */
@Composable
fun draftBack(dirty: Boolean, entity: String, onBack: () -> Unit): () -> Unit {
    var confirm by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { confirm = true }
    if (confirm) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your edits to this $entity haven't been saved.",
            confirmLabel = "Discard",
            onConfirm = onBack,
            onDismiss = { confirm = false },
            dismissLabel = "Keep editing",
        )
    }
    return { if (dirty) confirm = true else onBack() }
}
