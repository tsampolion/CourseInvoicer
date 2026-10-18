package gr.edusuite.invoicer.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import gr.edusuite.invoicer.ui.theme.Space

@Composable
fun DamagedDatabaseBanner(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = Space.l),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(Space.l)) {
            Text("The database was damaged and has been reset. Your old file was kept. Import your last backup in Settings.")
            Row {
                TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
