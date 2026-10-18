package gr.edusuite.invoicer.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import gr.edusuite.invoicer.ui.common.animationsEnabled
import gr.edusuite.invoicer.ui.theme.Motion
import gr.edusuite.invoicer.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * How many columns the Home grid has (guide §6): as many [minDp] columns, with [gapDp] between them, as
 * [widthDp] fits, at least two and at most one per tile. [widthDp] is the grid's own width, inside the
 * 16dp screen edges, so the guide's "under 360dp" is under 328dp here. At 200% font scale or on a narrow
 * screen the grid is one column.
 */
fun homeColumns(widthDp: Float, fontScale: Float, tiles: Int, minDp: Float = 160f, gapDp: Float = 8f): Int {
    if (fontScale >= 2f || widthDp < 328f) return 1
    val fits = ((widthDp + gapDp) / (minDp + gapDp)).toInt()
    return fits.coerceIn(2, maxOf(2, tiles))
}

/**
 * Where each tile of a masonry grid goes, as (column, y), for tiles of the given [heights] in order. Each
 * tile goes into the shortest column so far, the leftmost on a tie, [gap] below the tile above it.
 */
fun masonryPlacement(heights: List<Int>, columns: Int, gap: Int): List<Pair<Int, Int>> {
    val bottoms = IntArray(columns.coerceAtLeast(1))
    return heights.map { height ->
        val column = bottoms.indices.minBy { bottoms[it] }
        val y = bottoms[column]
        bottoms[column] = y + height + gap
        column to y
    }
}

/**
 * The Home tile grid: [columns] equal columns [gap] apart, each tile in the shortest column
 * ([masonryPlacement]). Every tile is composed, unlike a lazy grid, so tests can scroll to any of them.
 */
@Composable
fun HomeGrid(columns: Int, modifier: Modifier = Modifier, gap: Dp = Space.s, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val count = columns.coerceAtLeast(1)
        val width = constraints.maxWidth
        val columnWidth = ((width - gapPx * (count - 1)) / count).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(columnWidth)) }
        val spots = masonryPlacement(placeables.map { it.height }, count, gapPx)
        val height = placeables.indices.maxOfOrNull { spots[it].second + placeables[it].height } ?: 0
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEachIndexed { i, placeable ->
                val (column, y) = spots[i]
                placeable.place(column * (columnWidth + gapPx), y)
            }
        }
    }
}

/**
 * Makes a tile glide from its old place to its new one ([Motion.settle]) when the grid rearranges, for
 * example when the column count changes on rotation (guide §5, *Lists*). The first placement never
 * animates, and with *Remove animations* on the tile jumps (no offset at all).
 */
@Composable
fun Modifier.animatePlacement(): Modifier {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<IntOffset?>(null) }
    // Created at the first placement, so the tile starts where it is laid out.
    val position = remember { PlacementHolder() }
    if (!animationsEnabled()) return this
    return this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val goal = target ?: return@offset IntOffset.Zero
            val current = position.animatable ?: Animatable(goal, IntOffset.VectorConverter).also { position.animatable = it }
            if (current.targetValue != goal) scope.launch { current.animateTo(goal, Motion.settle()) }
            current.value - goal
        }
}

private class PlacementHolder {
    var animatable: Animatable<IntOffset, AnimationVector2D>? = null
}
