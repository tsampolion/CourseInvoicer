package gr.edusuite.invoicer.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import gr.edusuite.invoicer.ui.theme.Space

@Composable
fun StartupErrorScreen(message: String) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.padding(Space.xl), contentAlignment = Alignment.Center) { Text(message) }
    }
}
