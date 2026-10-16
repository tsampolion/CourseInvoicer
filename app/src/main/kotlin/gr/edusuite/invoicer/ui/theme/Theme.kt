package gr.edusuite.invoicer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The EduSuite Material 3 theme with Course Invoicer's purple accent (docs/EDUSUITE_STYLE_GUIDE.md).
 * Wallpaper (dynamic) colour is deliberately off, so the accent identifies the app on every phone.
 */
@Composable
fun InvoicerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val extras = if (darkTheme) DarkInvoicerColors else LightInvoicerColors
    CompositionLocalProvider(LocalInvoicerColors provides extras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = InvoicerTypography,
            shapes = InvoicerShapes,
            content = content,
        )
    }
}

/** Suite extras that Material's colour scheme has no slot for: `Invoicer.colors.line`, `.muted`, …. */
object Invoicer {
    val colors: InvoicerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalInvoicerColors.current
}
