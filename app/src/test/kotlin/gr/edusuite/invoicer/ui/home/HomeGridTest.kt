package gr.edusuite.invoicer.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGridTest {
    @Test fun columnsFollowWidth() {
        assertEquals(2, homeColumns(328f, 1f, 5)) // 360dp phone minus the 32dp edges
        assertEquals(2, homeColumns(380f, 1f, 5))
        assertEquals(4, homeColumns(768f, 1f, 5)) // landscape phone
        assertEquals(5, homeColumns(1248f, 1f, 5)) // capped at the tile count
    }

    @Test fun oneColumnAtLargeFontOrNarrow() {
        assertEquals(1, homeColumns(768f, 2f, 5))
        assertEquals(1, homeColumns(300f, 1f, 5))
    }

    @Test fun masonryFillsShortestColumn() {
        assertEquals(
            listOf(0 to 0, 1 to 0, 1 to 108, 0 to 168),
            masonryPlacement(listOf(160, 100, 100, 100), columns = 2, gap = 8),
        )
    }

    @Test fun homeOrderKeepsTheTwoColumnBento() {
        // Lessons, Statements, Students, Dashboard, Groups: tall 184, short 120.
        val spots = masonryPlacement(listOf(184, 184, 120, 184, 120), columns = 2, gap = 8)
        assertEquals(listOf(0, 1, 0, 1, 0), spots.map { it.first })
    }
}
