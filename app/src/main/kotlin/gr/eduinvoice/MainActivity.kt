package gr.eduinvoice

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.eduinvoice.core.format.Formats
import gr.eduinvoice.core.model.ThemeMode
import gr.eduinvoice.ui.EduInvoiceApp
import gr.eduinvoice.ui.common.LocalAppContainer
import gr.eduinvoice.ui.common.LocalFormats
import gr.eduinvoice.ui.theme.EduInvoiceTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EduInvoiceApplication).container
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keep system bar icons readable when the in-app theme differs from the system theme.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }
            val formats = remember(settings.currencyCode) { Formats(Locale.getDefault(), settings.currencyCode) }
            CompositionLocalProvider(LocalAppContainer provides container, LocalFormats provides formats) {
                EduInvoiceTheme(darkTheme = darkTheme) {
                    EduInvoiceApp()
                }
            }
        }
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
