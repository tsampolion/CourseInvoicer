package gr.edusuite.invoicer.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * EduSuite motion specs (guide §5). Nothing loops and nothing runs longer than 300 ms. When the system's
 * animator duration scale is 0 (Remove animations), Compose's animations jump to their end state.
 */
object Motion {
    const val QUICK_MS = 150
    const val CONTENT_MS = 250
    const val SCREEN_MS = 300

    /** Scale of a pressed button or tile. */
    const val PRESSED_SCALE = 0.96f

    /** State changes: press, toggle, selection. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)

    /** Sizes and positions. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    /** Colour and alpha changes. */
    fun <T> quick(): FiniteAnimationSpec<T> = tween(QUICK_MS, easing = FastOutSlowInEasing)

    /** Content that appears or expands. */
    fun <T> content(): FiniteAnimationSpec<T> = tween(CONTENT_MS, easing = FastOutSlowInEasing)

    /** Screen push and pop. */
    fun <T> screen(): FiniteAnimationSpec<T> = tween(SCREEN_MS, easing = FastOutSlowInEasing)
}
