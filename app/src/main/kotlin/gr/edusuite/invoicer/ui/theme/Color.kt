package gr.edusuite.invoicer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// EduSuite colour tokens (docs/EDUSUITE_STYLE_GUIDE.md, "Colour"). The neutrals and the warn/ok pairs are
// shared by every suite app; only the accent is Course Invoicer's own. Contrast ratios are listed in the guide.

// Shared neutrals, light.
private val PaperLight = Color(0xFFF2F5F2)
private val SurfaceLight = Color(0xFFFBFCFB)
private val SurfaceLowestLight = Color(0xFFFFFFFF)
private val ContainerLight = Color(0xFFE9EEEA)
private val ContainerHighLight = Color(0xFFE3E9E5)
private val ContainerHighestLight = Color(0xFFDDE4DF)
private val LineLight = Color(0xFFD2DAD5)
private val OutlineLight = Color(0xFF7D8A83)
private val MutedLight = Color(0xFF56625B)
private val InkLight = Color(0xFF1A211D)
private val InverseSurfaceLight = Color(0xFF2C332F)
private val InverseInkLight = Color(0xFFEDF1EE)

// Shared neutrals, dark.
private val PaperDark = Color(0xFF0F1413)
private val SurfaceDark = Color(0xFF161C1A)
private val SurfaceLowestDark = Color(0xFF0A0E0D)
private val ContainerDark = Color(0xFF1A2120)
private val ContainerHighDark = Color(0xFF212927)
private val ContainerHighestDark = Color(0xFF28312E)
private val LineDark = Color(0xFF2F3A36)
private val OutlineDark = Color(0xFF7F8C86)
private val MutedDark = Color(0xFF9DABA4)
private val InkDark = Color(0xFFE3EAE6)

// Semantic: warn (rust) and ok (moss).
private val WarnLight = Color(0xFF9E4A24)
private val OnWarnLight = Color(0xFFFFFFFF)
private val WarnSoftLight = Color(0xFFF7E4DA)
private val OnWarnSoftLight = Color(0xFF3E1A0A)
private val OkLight = Color(0xFF3E6E47)
private val OnOkLight = Color(0xFFFFFFFF)
private val OkSoftLight = Color(0xFFDCEBDF)
private val OnOkSoftLight = Color(0xFF15321C)

private val WarnDark = Color(0xFFEE9C78)
private val OnWarnDark = Color(0xFF3E1A0A)
private val WarnSoftDark = Color(0xFF3D2419)
private val OnWarnSoftDark = Color(0xFFF7E4DA)
private val OkDark = Color(0xFF8FC79A)
private val OnOkDark = Color(0xFF0F2A17)
private val OkSoftDark = Color(0xFF1D3424)
private val OnOkSoftDark = Color(0xFFDCEBDF)

// Course Invoicer accent (purple).
private val AccentLight = Color(0xFF5B3FA0)
private val OnAccentLight = Color(0xFFFFFFFF)
private val AccentSoftLight = Color(0xFFE9E2FA)
private val OnAccentSoftLight = Color(0xFF2B1B5E)

private val AccentDark = Color(0xFFCDBEFF)
private val OnAccentDark = Color(0xFF2B1B5E)
private val AccentSoftDark = Color(0xFF3B2D6B)
private val OnAccentSoftDark = Color(0xFFE9E2FA)

/**
 * Suite tokens that Material 3's [ColorScheme] has no slot for. Read them as `Invoicer.colors`.
 *
 * The same tokens are also mapped into the scheme (accent → primary, ok → tertiary, warn → error,
 * line → outlineVariant, muted → onSurfaceVariant), so stock Material components follow the suite look.
 */
@Immutable
data class InvoicerColors(
    /** The page background. */
    val paper: Color,
    /** 1dp borders and dividers; used instead of shadows. */
    val line: Color,
    /** Secondary text: captions, eyebrows, hints. */
    val muted: Color,
    /** Soft accent container, e.g. a tinted home button or an empty-state badge. */
    val accentSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val ok: Color,
    val okSoft: Color,
)

internal val LightInvoicerColors = InvoicerColors(
    paper = PaperLight,
    line = LineLight,
    muted = MutedLight,
    accentSoft = AccentSoftLight,
    warn = WarnLight,
    warnSoft = WarnSoftLight,
    ok = OkLight,
    okSoft = OkSoftLight,
)

internal val DarkInvoicerColors = InvoicerColors(
    paper = PaperDark,
    line = LineDark,
    muted = MutedDark,
    accentSoft = AccentSoftDark,
    warn = WarnDark,
    warnSoft = WarnSoftDark,
    ok = OkDark,
    okSoft = OkSoftDark,
)

internal val LocalInvoicerColors = staticCompositionLocalOf { LightInvoicerColors }

internal val LightColorScheme: ColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = OnAccentLight,
    primaryContainer = AccentSoftLight,
    onPrimaryContainer = OnAccentSoftLight,
    inversePrimary = AccentDark,
    secondary = MutedLight,
    onSecondary = SurfaceLowestLight,
    secondaryContainer = AccentSoftLight,
    onSecondaryContainer = OnAccentSoftLight,
    tertiary = OkLight,
    onTertiary = OnOkLight,
    tertiaryContainer = OkSoftLight,
    onTertiaryContainer = OnOkSoftLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = PaperLight,
    onSurface = InkLight,
    surfaceVariant = ContainerHighLight,
    onSurfaceVariant = MutedLight,
    surfaceTint = AccentLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseInkLight,
    error = WarnLight,
    onError = OnWarnLight,
    errorContainer = WarnSoftLight,
    onErrorContainer = OnWarnSoftLight,
    outline = OutlineLight,
    outlineVariant = LineLight,
    surfaceBright = SurfaceLight,
    surfaceDim = ContainerHighestLight,
    surfaceContainerLowest = SurfaceLowestLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = ContainerLight,
    surfaceContainerHigh = ContainerHighLight,
    surfaceContainerHighest = ContainerHighestLight,
)

internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = OnAccentDark,
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = OnAccentSoftDark,
    inversePrimary = AccentLight,
    secondary = MutedDark,
    onSecondary = PaperDark,
    secondaryContainer = AccentSoftDark,
    onSecondaryContainer = OnAccentSoftDark,
    tertiary = OkDark,
    onTertiary = OnOkDark,
    tertiaryContainer = OkSoftDark,
    onTertiaryContainer = OnOkSoftDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = ContainerHighDark,
    onSurfaceVariant = MutedDark,
    surfaceTint = AccentDark,
    inverseSurface = InkDark,
    inverseOnSurface = InverseSurfaceLight,
    error = WarnDark,
    onError = OnWarnDark,
    errorContainer = WarnSoftDark,
    onErrorContainer = OnWarnSoftDark,
    outline = OutlineDark,
    outlineVariant = LineDark,
    surfaceBright = ContainerHighestDark,
    surfaceDim = PaperDark,
    surfaceContainerLowest = SurfaceLowestDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = ContainerDark,
    surfaceContainerHigh = ContainerHighDark,
    surfaceContainerHighest = ContainerHighestDark,
)
