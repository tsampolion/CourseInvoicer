package gr.edusuite.invoicer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gr.edusuite.invoicer.ui.theme.Invoicer
import gr.edusuite.invoicer.ui.theme.Space

/**
 * What a list shows when it has nothing yet (docs/EDUSUITE_STYLE_GUIDE.md, "EmptyState"): a 120dp
 * circle in a tile tint holding a 48dp [icon] in `onTile`, a [title], a short muted [message] and, when
 * [actionLabel] is set, a pill button that leads to adding the first item. Centred, 16dp between parts.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    // Scrolls so the parts stay reachable at large font sizes or in landscape.
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = Space.xl, vertical = Space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Box(
                Modifier
                    .size(120.dp)
                    .background(Invoicer.colors.tileLilac, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // Decorative: the title says the same thing in words.
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Invoicer.colors.onTile,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Invoicer.colors.muted,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                PillButton(actionLabel, onClick = onAction)
            }
        }
    }
}
