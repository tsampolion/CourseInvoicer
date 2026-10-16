package gr.edusuite.invoicer.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.edusuite.invoicer.R
import gr.edusuite.invoicer.ui.theme.Invoicer
import gr.edusuite.invoicer.ui.theme.Mono

// Shared EduSuite building blocks (docs/EDUSUITE_STYLE_GUIDE.md, "Components").

/** Which tint a [HomeButton] uses. */
enum class SuiteTone { Accent, Ok, Warn, Neutral }

/** A flat panel: 6dp corners and a 1dp line border instead of a shadow. */
@Composable
fun SuitePanel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = color,
        border = BorderStroke(1.dp, Invoicer.colors.line),
        content = content,
    )
}

/** A small uppercase label above a group: labelSmall, SemiBold, 1sp tracking, muted. Read as a heading. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Invoicer.colors.muted) {
    val locale = LocalFormats.current.locale
    Text(
        text = text.uppercase(locale),
        modifier = modifier.semantics {
            heading()
            // TalkBack reads the original casing; all-caps words can be spelled out letter by letter.
            contentDescription = text
        },
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
        maxLines = 1,
    )
}

/** A titled group: an [Eyebrow] with an optional trailing [action], then the [content]. */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(title, Modifier.weight(1f))
            if (action != null) action()
        }
        content()
    }
}

/** Money, dates and counts in the monospace face, so figures line up. */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        fontFamily = Mono,
        maxLines = maxLines,
        style = style,
    )
}

/**
 * The app's launcher art on a rounded accent tile (22dp corners at the default 88dp). The foreground
 * vector is drawn at 1.5× and clipped, which crops the adaptive icon's safe-zone margin. Decorative:
 * the app name next to it is what TalkBack reads.
 */
@Composable
fun Logo(modifier: Modifier = Modifier, size: Dp = 88.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(colorResource(R.color.launcher_tile)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * A full-width tinted button with a title and a live [subtitle] (for example "3 lessons this week").
 * When its section has nothing in it yet, pass [empty]: the tint drops to 30% and the text to 60%,
 * and the button still works, so it can lead to adding the first item.
 */
@Composable
fun HomeButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    empty: Boolean = false,
    tone: SuiteTone = SuiteTone.Accent,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        SuiteTone.Accent -> scheme.primaryContainer to scheme.onPrimaryContainer
        SuiteTone.Ok -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        SuiteTone.Warn -> scheme.errorContainer to scheme.onErrorContainer
        SuiteTone.Neutral -> scheme.surfaceContainer to scheme.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { role = Role.Button },
        shape = MaterialTheme.shapes.medium,
        color = if (empty) container.copy(alpha = 0.3f) else container,
        contentColor = if (empty) content.copy(alpha = 0.6f) else content,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * A short how-to card: a serif title, a line or two of help, and optional action and dismiss buttons.
 */
@Composable
fun GuideCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
) {
    SuitePanel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) { Icon(AppIcons.Close, contentDescription = "Dismiss") }
                }
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = Invoicer.colors.muted,
                modifier = Modifier.padding(end = 12.dp),
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
