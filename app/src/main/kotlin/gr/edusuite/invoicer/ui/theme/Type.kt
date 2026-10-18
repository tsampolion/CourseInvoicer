package gr.edusuite.invoicer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import gr.edusuite.invoicer.R

/**
 * Outfit (SIL Open Font License, licence text in assets/licenses/outfit_ofl.txt), bundled as the variable
 * font that Google Fonts ships and used at Regular, Medium and SemiBold (guide §3). minSdk 26 supports font
 * variation settings, so one file serves all three weights.
 */
val Outfit: FontFamily = FontFamily(
    outfit(FontWeight.Normal),
    outfit(FontWeight.Medium),
    outfit(FontWeight.SemiBold),
)

@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: FontWeight) = Font(
    resId = R.font.outfit_variable,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * Money, dates and counts use the system monospace font, so figures line up and a 1 is never read as a 7
 * (guide §3). Headline figures add SemiBold; the size follows the surrounding style.
 */
val Mono: FontFamily = FontFamily.Monospace

private val Base = Typography()

private fun TextStyle.outfit(weight: FontWeight, size: Int, line: Int): TextStyle =
    copy(fontFamily = Outfit, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun TextStyle.outfit(weight: FontWeight): TextStyle = copy(fontFamily = Outfit, fontWeight = weight)

/**
 * EduSuite type scale (guide §3): Outfit everywhere; SemiBold for the home greeting and screen titles,
 * Medium for tile, card and row titles and for labels, Regular for body text.
 */
internal val InvoicerTypography = Typography(
    displayLarge = Base.displayLarge.outfit(FontWeight.SemiBold),
    displayMedium = Base.displayMedium.outfit(FontWeight.SemiBold),
    displaySmall = Base.displaySmall.outfit(FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.outfit(FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.outfit(FontWeight.SemiBold, 28, 36),
    headlineSmall = Base.headlineSmall.outfit(FontWeight.Medium, 20, 26),
    titleLarge = Base.titleLarge.outfit(FontWeight.SemiBold, 22, 28),
    titleMedium = Base.titleMedium.outfit(FontWeight.Medium, 16, 22),
    titleSmall = Base.titleSmall.outfit(FontWeight.Medium),
    bodyLarge = Base.bodyLarge.outfit(FontWeight.Normal, 16, 24),
    bodyMedium = Base.bodyMedium.outfit(FontWeight.Normal, 14, 20),
    bodySmall = Base.bodySmall.outfit(FontWeight.Normal, 12, 16),
    labelLarge = Base.labelLarge.outfit(FontWeight.Medium, 14, 20),
    labelMedium = Base.labelMedium.outfit(FontWeight.Medium, 12, 16),
    labelSmall = Base.labelSmall.outfit(FontWeight.Medium, 11, 16),
)
