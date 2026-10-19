package gr.edusuite.invoicer.ui.common

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import gr.edusuite.invoicer.R
import gr.edusuite.invoicer.ui.theme.Invoicer
import gr.edusuite.invoicer.ui.theme.Mono
import gr.edusuite.invoicer.ui.theme.Motion
import gr.edusuite.invoicer.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import gr.edusuite.invoicer.ui.theme.TabularFigures

// Shared EduSuite building blocks (docs/EDUSUITE_STYLE_GUIDE.md, §5 "Motion" and §6 "Components").

// ---------------------------------------------------------------------------------------------------
// Motion helpers (guide §5)
// ---------------------------------------------------------------------------------------------------

/**
 * False when the system's *Remove animations* is on (animator duration scale 0). Compose already jumps
 * its animations to the end then; this lets a component skip a transition it builds itself.
 */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/**
 * The press motion for buttons and tiles: scales to 0.96 while pressed and springs back
 * ([Motion.bouncy]), with a light haptic tick when the press starts. Haptics follow the system's
 * touch-feedback setting. Pass the same [interactionSource] the clickable uses.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, haptic: Boolean = true): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PRESSED_SCALE else 1f,
        animationSpec = Motion.bouncy(),
        label = "press",
    )
    val hapticFeedback = LocalHapticFeedback.current
    if (haptic) {
        LaunchedEffect(pressed) {
            if (pressed) hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
        }
    }
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private data class Figure(val value: Long, val text: String)

/**
 * A rolling figure (guide §5): money or a count in monospace that slides in from below when [value]
 * rises and from above when it falls. [text] is what is shown; [value] only gives the direction, for
 * example `Money.cents` or a count.
 */
@Composable
fun RollingFigure(
    text: String,
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    val animate = animationsEnabled()
    AnimatedContent(
        targetState = Figure(value, text),
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            if (!animate || targetState.value == initialState.value) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val rising = targetState.value > initialState.value
                (
                    slideInVertically(Motion.content()) { height -> if (rising) height else -height } +
                        fadeIn(Motion.content())
                    ) togetherWith (
                    slideOutVertically(Motion.content()) { height -> if (rising) -height else height } +
                        fadeOut(Motion.content())
                    ) using SizeTransform(clip = true)
            }
        },
        label = "figure",
    ) { figure ->
        MoneyText(figure.text, style = style, color = color, fontWeight = fontWeight, maxLines = 1)
    }
}

/**
 * A Material 3 switch with the suite's toggle motion (guide §5): the thumb icon cross-fades from ✕ to
 * ✓, and a light haptic tick (ToggleOn / ToggleOff) follows each change. The thumb's spring and the
 * track's colour fade are the Material component's own.
 */
@Composable
fun SuiteSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var last by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked != last) {
            hapticFeedback.performHapticFeedback(
                if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
            )
            last = checked
        }
    }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            Crossfade(targetState = checked, animationSpec = Motion.quick(), label = "thumb") { on ->
                Icon(
                    if (on) AppIcons.Check else AppIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------------
// Text and grouping
// ---------------------------------------------------------------------------------------------------

/** Which tint a [HomeButton] uses. */
enum class SuiteTone { Accent, Ok, Warn, Neutral }

/** A flat panel (list groups, panels): the surface colour with 16dp corners, no border and no shadow. */
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
        content = content,
    )
}

/** A label above a group: labelMedium Medium in muted, sentence case. Read as a heading. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Invoicer.colors.muted) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        color = color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}

/** A titled group: an [Eyebrow] with an optional trailing [action], then the [content], 8dp apart. */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
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
        style = style.copy(fontFeatureSettings = TabularFigures),
    )
}

/**
 * The app's launcher art on a rounded accent tile (a quarter of its size: 22dp at the default 88dp).
 * The foreground vector is drawn at 1.5× and clipped, which crops the adaptive icon's safe-zone
 * margin. Decorative: the app name next to it is what TalkBack reads.
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

// ---------------------------------------------------------------------------------------------------
// Chips and buttons
// ---------------------------------------------------------------------------------------------------

/** What a [StatusChip] says: neutral, good (paid, done) or a warning (unpaid, overdue). */
enum class ChipTone { Neutral, Good, Warning }

/**
 * A status pill (guide §6), `labelSmall` Medium: *Unpaid*, *Paid*, *Draft*. On paper or surface it
 * takes a soft tint for its [tone]; on a tile ([onTile]) it is the surface colour at 70%. The word
 * carries the meaning, never the colour alone.
 */
@Composable
fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier, onTile: Boolean = false) {
    val colors = Invoicer.colors
    val (container, content) = when {
        onTile -> colors.surface.copy(alpha = 0.7f) to colors.onTile
        tone == ChipTone.Good -> colors.okSoft to colors.onOkSoft
        tone == ChipTone.Warning -> colors.warnSoft to colors.onWarnSoft
        else -> colors.surfaceContainer to colors.ink
    }
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Space.s, vertical = 2.dp),
        )
    }
}

/** The look of a [PillButton]: accent fill, surfaceContainer fill with ink text, or text only. */
enum class PillStyle { Primary, Secondary, Tertiary }

/** A pill button, 48dp tall, with the press motion (guide §6). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = Invoicer.colors
    when (style) {
        PillStyle.Primary -> PillButtonImpl(text, onClick, modifier, enabled, icon, colors.accent, colors.onAccent)
        PillStyle.Secondary -> PillButtonImpl(text, onClick, modifier, enabled, icon, colors.surfaceContainer, colors.ink)
        PillStyle.Tertiary -> {
            val interaction = remember { MutableInteractionSource() }
            TextButton(
                onClick = onClick,
                modifier = modifier.heightIn(min = 48.dp).pressScale(interaction),
                enabled = enabled,
                shape = CircleShape,
                interactionSource = interaction,
            ) { PillLabel(text, icon) }
        }
    }
}

@Composable
private fun PillButtonImpl(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    container: Color,
    content: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).pressScale(interaction),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        elevation = null,
        interactionSource = interaction,
    ) { PillLabel(text, icon) }
}

@Composable
private fun PillLabel(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s))
    }
    Text(text, maxLines = 1)
}

// ---------------------------------------------------------------------------------------------------
// Tiles
// ---------------------------------------------------------------------------------------------------

/**
 * The base block (guide §6): a tile colour, 24dp corners, 16dp padding, no border. An optional [chip]
 * and [icon] sit at the top; the [title] (`headlineSmall`, a heading), a [subtitle] in `onTileMuted`
 * and an optional [figure] at the bottom. The whole tile is one touch target with the press motion,
 * and TalkBack reads it as one item.
 *
 * An [empty] tile dims: background at 30%, text and icon at 60%. It still opens, so the user can add
 * the first item. Give it a height through [modifier] for a tall tile; the top and bottom parts then
 * spread apart.
 */
@Composable
fun Tile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Invoicer.colors.tileLilac,
    subtitle: String? = null,
    icon: ImageVector? = null,
    empty: Boolean = false,
    chip: (@Composable () -> Unit)? = null,
    /** Overrides the muted subtitle colour, for a figure that needs attention (warn). */
    subtitleColor: Color? = null,
    figure: (@Composable () -> Unit)? = null,
) {
    val colors = Invoicer.colors
    val interaction = remember { MutableInteractionSource() }
    val alpha = if (empty) 0.6f else 1f
    Surface(
        onClick = onClick,
        modifier = modifier.pressScale(interaction),
        shape = MaterialTheme.shapes.large,
        color = if (empty) color.copy(alpha = 0.3f) else color,
        contentColor = colors.onTile.copy(alpha = alpha),
        interactionSource = interaction,
    ) {
        Column(
            Modifier.padding(Space.l),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (chip != null || icon != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.weight(1f))
                    if (chip != null) chip()
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = (subtitleColor ?: colors.onTileMuted).copy(alpha = alpha),
                    )
                }
                if (figure != null) figure()
            }
        }
    }
}

/**
 * The hero tile (guide §6): at most one per screen, in the accent fill. The one thing to do next, with
 * a pill [actionLabel] button in the surface colour.
 */
@Composable
fun HeroTile(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    val colors = Invoicer.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.accent,
        contentColor = colors.onAccent,
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            PillButtonImpl(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier,
                enabled = true,
                icon = null,
                container = colors.surface,
                content = colors.ink,
            )
        }
    }
}

/**
 * One figure in a [StatTileRow]: a [label] over a [figure] in mono SemiBold with an optional [unit] in
 * sans (*€480* · *12 lessons*). [value] gives the rolling direction when the figure changes (cents or a
 * count). [figureColor] defaults to the tile text colour; pass warn or ok where it means something,
 * and say it in the label too.
 */
data class Stat(
    val label: String,
    val figure: String,
    val unit: String? = null,
    val value: Long = 0,
    val color: Color = Color.Unspecified,
    val figureColor: Color = Color.Unspecified,
)

/** A single stat tile: 16dp corners, [color] fill (the stat's own tint when it sets one). */
@Composable
fun StatTile(
    stat: Stat,
    modifier: Modifier = Modifier,
    color: Color = Invoicer.colors.tileLilac,
    labelColor: Color = Invoicer.colors.onTileMuted,
    contentColor: Color = Invoicer.colors.onTile,
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = if (stat.color != Color.Unspecified) stat.color else color,
        contentColor = contentColor,
    ) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(stat.label, style = MaterialTheme.typography.bodySmall, color = labelColor)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                RollingFigure(
                    text = stat.figure,
                    value = stat.value,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (stat.figureColor != Color.Unspecified) stat.figureColor else LocalContentColor.current,
                    fontWeight = FontWeight.SemiBold,
                )
                if (stat.unit != null) {
                    Text(
                        stat.unit,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Two or three stat tiles side by side (guide §6), each in its own tint (lilac, mint, sky unless a
 * [Stat] sets its colour), all as tall as the tallest.
 */
@Composable
fun StatTileRow(stats: List<Stat>, modifier: Modifier = Modifier) {
    val colors = Invoicer.colors
    val tints = listOf(colors.tileLilac, colors.tileMint, colors.tileSky)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        stats.forEachIndexed { index, stat ->
            StatTile(
                stat = stat,
                color = tints[index % tints.size],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * A full-width section tile with a title and a live [subtitle] (for example "3 lessons this week"),
 * kept for the screens that list sections in one column. When its section has nothing in it yet, pass
 * [empty]: the tint drops to 30% and the text to 60%, and the tile still opens, so it can lead to
 * adding the first item.
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
    val colors = Invoicer.colors
    val container = when (tone) {
        SuiteTone.Accent -> colors.tileLilac
        SuiteTone.Ok -> colors.tileMint
        SuiteTone.Warn -> colors.tileApricot
        SuiteTone.Neutral -> colors.surfaceContainer
    }
    val content = if (tone == SuiteTone.Neutral) colors.ink else colors.onTile
    val muted = if (tone == SuiteTone.Neutral) colors.muted else colors.onTileMuted
    val alpha = if (empty) 0.6f else 1f
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .pressScale(interaction)
            .semantics { role = Role.Button },
        shape = MaterialTheme.shapes.large,
        color = if (empty) container.copy(alpha = 0.3f) else container,
        contentColor = content.copy(alpha = alpha),
        interactionSource = interaction,
    ) {
        Row(
            Modifier.padding(horizontal = Space.l, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = muted.copy(alpha = alpha))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// Day strip
// ---------------------------------------------------------------------------------------------------

/**
 * Seven pill day chips (guide §6): weekday over date, the date in mono. Today has a small dot above the
 * weekday; days in [hasLessons] have a dot below; the [selected] day has the ink fill, which slides from
 * the old chip to the new one as one indicator. A horizontal swipe calls [onPreviousWeek] or
 * [onNextWeek] when they are set.
 */
@Composable
fun DayStrip(
    days: List<LocalDate>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    hasLessons: (LocalDate) -> Boolean = { false },
    onPreviousWeek: (() -> Unit)? = null,
    onNextWeek: (() -> Unit)? = null,
) {
    if (days.isEmpty()) return
    val colors = Invoicer.colors
    val locale = LocalFormats.current.locale
    val weekday = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val spoken = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val previous by rememberUpdatedState(onPreviousWeek)
    val next by rememberUpdatedState(onNextWeek)
    val swipe = if (onPreviousWeek != null || onNextWeek != null) {
        Modifier.pointerInput(Unit) {
            var total = 0f
            val threshold = 48.dp.toPx()
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (total > threshold) previous?.invoke() else if (total < -threshold) next?.invoke()
                    total = 0f
                },
                onDragCancel = { total = 0f },
            ) { _, dragAmount -> total += dragAmount }
        }
    } else {
        Modifier
    }
    BoxWithConstraints(modifier.fillMaxWidth().then(swipe)) {
        val chipWidth = maxWidth / days.size
        val index = days.indexOf(selected)
        val offset by animateDpAsState(
            targetValue = chipWidth * index.coerceAtLeast(0),
            animationSpec = Motion.bouncy(),
            label = "day indicator",
        )
        if (index >= 0) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .offset(x = offset)
                        .width(chipWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .background(colors.ink, CircleShape),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { day ->
                val isSelected = day == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colors.onSelected else colors.ink,
                    animationSpec = Motion.quick(),
                    label = "day text",
                )
                val mutedColor by animateColorAsState(
                    targetValue = if (isSelected) colors.onSelected else colors.muted,
                    animationSpec = Motion.quick(),
                    label = "day weekday",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(day) })
                        .semantics(mergeDescendants = true) { contentDescription = day.format(spoken) }
                        .padding(vertical = Space.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Dot(visible = day == today, color = textColor)
                    Text(day.format(weekday), style = MaterialTheme.typography.labelSmall, color = mutedColor, maxLines = 1)
                    MoneyText(
                        day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Dot(visible = hasLessons(day), color = if (isSelected) textColor else colors.accent)
                }
            }
        }
    }
}

@Composable
private fun Dot(visible: Boolean, color: Color) {
    Box(
        Modifier
            .size(4.dp)
            .background(if (visible) color else Color.Transparent, CircleShape),
    )
}

// ---------------------------------------------------------------------------------------------------
// Lists
// ---------------------------------------------------------------------------------------------------

/** A group of [ListRow]s on a 16dp-corner surface (guide §6). Put a [ListDivider] between rows. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = Invoicer.colors.surface) {
        Column(content = content)
    }
}

/** A hairline `line` divider, inset to the text of a [ListRow] that has an icon ([inset]). */
@Composable
fun ListDivider(modifier: Modifier = Modifier, inset: Boolean = true) {
    HorizontalDivider(
        modifier.padding(start = if (inset) Space.l + 24.dp + Space.l else Space.l),
        thickness = 1.dp,
        color = Invoicer.colors.line,
    )
}

/**
 * A list row (guide §6): a leading outlined [icon], a [title] (`titleMedium`), a muted [subtitle], and a
 * [trailing] value; a row with [onClick] and no trailing content shows a chevron.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val body: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = Space.l, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Invoicer.colors.muted)
                }
            }
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(AppIcons.ChevronRight, contentDescription = null, tint = Invoicer.colors.muted)
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, color = Color.Transparent) { body() }
    } else {
        Box(modifier.semantics(mergeDescendants = true) {}) { body() }
    }
}

/**
 * A warning banner (guide §6): a full-width warnSoft block with 16dp corners, on-warnSoft [text] and an
 * optional text action. For something the user should act on soon; errors from an action go in a snackbar.
 */
@Composable
fun WarningBanner(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Invoicer.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.warnSoft,
        contentColor = colors.onWarnSoft,
    ) {
        Row(
            Modifier.padding(start = Space.l, end = Space.s, top = Space.xs, bottom = Space.xs).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, Modifier.weight(1f).padding(vertical = Space.s), style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, colors = ButtonDefaults.textButtonColors(contentColor = colors.onWarnSoft)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// Guide card
// ---------------------------------------------------------------------------------------------------

/**
 * A short how-to on a tile (guide §6): a title, a line or two of `onTileMuted` help, and optional
 * action and dismiss buttons. It explains, it never blocks.
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
    val colors = Invoicer.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.tileSky,
        contentColor = colors.onTile,
    ) {
        Column(Modifier.padding(start = Space.l, top = Space.m, end = Space.xs, bottom = Space.s)) {
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
                color = colors.onTileMuted,
                modifier = Modifier.padding(end = Space.m),
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = colors.onTile) }
            }
        }
    }
}
