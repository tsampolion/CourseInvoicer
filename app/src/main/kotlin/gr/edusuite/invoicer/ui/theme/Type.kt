package gr.edusuite.invoicer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Money, dates and counts use the system monospace font, so figures line up in columns. */
val Mono: FontFamily = FontFamily.Monospace

private val Base = Typography()

private fun TextStyle.serif(): TextStyle = copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)

/**
 * EduSuite type scale: system serif SemiBold for display, headline and title styles (28sp home welcome,
 * 22sp screen titles, 20sp dialog and card titles); Material 3 defaults for body and label styles.
 */
internal val InvoicerTypography = Typography(
    displayLarge = Base.displayLarge.serif(),
    displayMedium = Base.displayMedium.serif(),
    displaySmall = Base.displaySmall.serif(),
    headlineLarge = Base.headlineLarge.serif(),
    headlineMedium = Base.headlineMedium.serif().copy(fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = Base.headlineSmall.serif().copy(fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = Base.titleLarge.serif().copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = Base.titleMedium.serif(),
    titleSmall = Base.titleSmall.serif(),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge,
    labelMedium = Base.labelMedium,
    labelSmall = Base.labelSmall,
)
