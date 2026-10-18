package gr.edusuite.invoicer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// EduSuite colour tokens (docs/EDUSUITE_STYLE_GUIDE.md, "Colour"). The neutrals and the warn/ok pairs are
// shared by every suite app; only the accent is Course Invoicer's own. Contrast ratios are listed in the guide.

// Shared neutrals, light (guide §2.1).
private val PaperLight = Color(0xFFF6F5FA)
private val SurfaceLight = Color(0xFFFFFFFF)
private val ContainerLight = Color(0xFFE8E6EF)
private val LineLight = Color(0xFFE4E2EA)
private val OutlineLight = Color(0xFF7A7686)
private val MutedLight = Color(0xFF5E5A6B)
private val InkLight = Color(0xFF1B1A22)

// Shared neutrals, dark.
private val PaperDark = Color(0xFF121118)
private val SurfaceDark = Color(0xFF1C1B22)
private val ContainerDark = Color(0xFF2A2832)
private val LineDark = Color(0xFF2E2C37)
private val OutlineDark = Color(0xFF8C889A)
private val MutedDark = Color(0xFFA9A5B5)
private val InkDark = Color(0xFFECEAF2)

// Tiles (guide §2.2). Dark mode uses its own deep set with light text, not the pastels dimmed.
private val TileLilacLight = Color(0xFFD9CCF5)
private val TileMintLight = Color(0xFFBFE8C8)
private val TileSkyLight = Color(0xFFBDE6F0)
private val TileApricotLight = Color(0xFFFFD39A)
private val TilePinkLight = Color(0xFFF4C6E6)
private val OnTileLight = Color(0xFF1B1A22)
private val OnTileMutedLight = Color(0xFF46424F)

private val TileLilacDark = Color(0xFF3A2E5C)
private val TileMintDark = Color(0xFF1F4A33)
private val TileSkyDark = Color(0xFF1B4350)
private val TileApricotDark = Color(0xFF5A3B12)
private val TilePinkDark = Color(0xFF55264A)
private val OnTileDark = Color(0xFFF3F1F8)
private val OnTileMutedDark = Color(0xFFD6D2E0)

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

// Course Invoicer accent (purple), guide §2.4.
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
 * line → outlineVariant, muted → onSurfaceVariant, ink → onSurface), so stock Material components follow
 * the suite look.
 */
@Immutable
data class InvoicerColors(
    /** The page background. */
    val paper: Color,
    /** Sheets, list groups and fields. */
    val surface: Color,
    /** Neutral tiles, bars, unselected chips. */
    val surfaceContainer: Color,
    /** Hairline dividers (decorative). */
    val line: Color,
    /** Secondary text on paper and surface. Use [onTileMuted] on a tile. */
    val muted: Color,
    /** Body text, titles and the selected fill. */
    val ink: Color,
    /** The app's accent, also `MaterialTheme.colorScheme.primary`. */
    val accent: Color,
    val onAccent: Color,
    /** Soft accent container, e.g. a tinted home button or an empty-state badge. */
    val accentSoft: Color,
    val onAccentSoft: Color,
    val warn: Color,
    val onWarn: Color,
    val warnSoft: Color,
    val onWarnSoft: Color,
    val ok: Color,
    val onOk: Color,
    val okSoft: Color,
    val onOkSoft: Color,
    val tileLilac: Color,
    val tileMint: Color,
    val tileSky: Color,
    val tileApricot: Color,
    val tilePink: Color,
    /** Text and icons on any tile. */
    val onTile: Color,
    /** Secondary text on any tile. */
    val onTileMuted: Color,
) {
    /** Paper text on the ink fill of a selected chip or day. */
    val onSelected: Color get() = paper
}

internal val LightInvoicerColors = InvoicerColors(
    paper = PaperLight,
    surface = SurfaceLight,
    surfaceContainer = ContainerLight,
    line = LineLight,
    muted = MutedLight,
    ink = InkLight,
    accent = AccentLight,
    onAccent = OnAccentLight,
    accentSoft = AccentSoftLight,
    onAccentSoft = OnAccentSoftLight,
    warn = WarnLight,
    onWarn = OnWarnLight,
    warnSoft = WarnSoftLight,
    onWarnSoft = OnWarnSoftLight,
    ok = OkLight,
    onOk = OnOkLight,
    okSoft = OkSoftLight,
    onOkSoft = OnOkSoftLight,
    tileLilac = TileLilacLight,
    tileMint = TileMintLight,
    tileSky = TileSkyLight,
    tileApricot = TileApricotLight,
    tilePink = TilePinkLight,
    onTile = OnTileLight,
    onTileMuted = OnTileMutedLight,
)

internal val DarkInvoicerColors = InvoicerColors(
    paper = PaperDark,
    surface = SurfaceDark,
    surfaceContainer = ContainerDark,
    line = LineDark,
    muted = MutedDark,
    ink = InkDark,
    accent = AccentDark,
    onAccent = OnAccentDark,
    accentSoft = AccentSoftDark,
    onAccentSoft = OnAccentSoftDark,
    warn = WarnDark,
    onWarn = OnWarnDark,
    warnSoft = WarnSoftDark,
    onWarnSoft = OnWarnSoftDark,
    ok = OkDark,
    onOk = OnOkDark,
    okSoft = OkSoftDark,
    onOkSoft = OnOkSoftDark,
    tileLilac = TileLilacDark,
    tileMint = TileMintDark,
    tileSky = TileSkyDark,
    tileApricot = TileApricotDark,
    tilePink = TilePinkDark,
    onTile = OnTileDark,
    onTileMuted = OnTileMutedDark,
)

internal val LocalInvoicerColors = staticCompositionLocalOf { LightInvoicerColors }

// Material 3 slots per the guide's tables (§2.1, §2.3, §2.4). Surface containers High and Highest equal
// surfaceContainer; there is no tonal elevation.
internal val LightColorScheme: ColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = OnAccentLight,
    primaryContainer = AccentSoftLight,
    onPrimaryContainer = OnAccentSoftLight,
    inversePrimary = AccentDark,
    secondary = MutedLight,
    onSecondary = SurfaceLight,
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
    surfaceVariant = ContainerLight,
    onSurfaceVariant = MutedLight,
    surfaceTint = AccentLight,
    inverseSurface = InkLight,
    inverseOnSurface = PaperLight,
    error = WarnLight,
    onError = OnWarnLight,
    errorContainer = WarnSoftLight,
    onErrorContainer = OnWarnSoftLight,
    outline = OutlineLight,
    outlineVariant = LineLight,
    surfaceBright = SurfaceLight,
    surfaceDim = ContainerLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = ContainerLight,
    surfaceContainerHigh = ContainerLight,
    surfaceContainerHighest = ContainerLight,
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
    surfaceVariant = ContainerDark,
    onSurfaceVariant = MutedDark,
    surfaceTint = AccentDark,
    inverseSurface = InkDark,
    inverseOnSurface = PaperDark,
    error = WarnDark,
    onError = OnWarnDark,
    errorContainer = WarnSoftDark,
    onErrorContainer = OnWarnSoftDark,
    outline = OutlineDark,
    outlineVariant = LineDark,
    surfaceBright = ContainerDark,
    surfaceDim = PaperDark,
    surfaceContainerLowest = PaperDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = ContainerDark,
    surfaceContainerHigh = ContainerDark,
    surfaceContainerHighest = ContainerDark,
)
