package gr.edusuite.invoicer.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScreenErrorNotice(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unable to load") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
fun MissingEntityScreen(message: String, onBack: () -> Unit) {
    ScreenScaffold(title = "Unable to open", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text(message)
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}
