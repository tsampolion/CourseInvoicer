package gr.edusuite.invoicer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * EduSuite shapes: 4dp for chips and fields, 6dp for cards and panels, a little more for dialogs and
 * sheets. The logo tile has its own 22dp radius (`Logo` in ui/common/SuiteComponents.kt).
 */
internal val InvoicerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(12.dp),
)
