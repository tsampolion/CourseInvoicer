package gr.edusuite.invoicer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * EduSuite shapes (guide §4): pills for chips and buttons (extraSmall, 50%), 12dp text fields, 16dp list
 * groups, panels and stat tiles, 24dp tiles, 28dp dialogs and sheets. The logo tile has its own radius, a
 * quarter of its size (`Logo` in ui/common/SuiteComponents.kt).
 */
internal val InvoicerShapes = Shapes(
    extraSmall = RoundedCornerShape(percent = 50),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
