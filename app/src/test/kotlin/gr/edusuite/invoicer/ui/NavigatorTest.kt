package gr.edusuite.invoicer.ui

import androidx.lifecycle.SavedStateHandle
import gr.edusuite.invoicer.ui.nav.Navigator
import gr.edusuite.invoicer.ui.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    @Test
    fun `every route survives encoding`() {
        val routes = listOf(
            Route.Home, Route.Dashboard, Route.Students, Route.Groups, Route.Lessons, Route.Invoices, Route.Settings,
            Route.StatementDetails,
            Route.StudentDetail(3), Route.StudentEdit(), Route.StudentEdit(4), Route.GroupEdit(5),
            Route.GroupLesson(6), Route.LessonEdit(7, 8), Route.LessonEdit(studentId = 9), Route.CreateInvoice(10),
        )
        routes.forEach { assertEquals(it, Route.decode(Route.encode(it))) }
        assertNull(Route.decode("student/abc"))
        assertNull(Route.decode("unknown"))
        assertEquals("dashboard", Route.encode(Route.Dashboard))
        assertEquals("statement-details", Route.encode(Route.StatementDetails))
    }

    @Test
    fun `sections open from home and back returns home`() {
        val navigator = Navigator(SavedStateHandle())
        assertEquals(listOf(Route.Home), navigator.backStack.map { it.route })

        navigator.navigate(Route.Dashboard)
        assertEquals(listOf(Route.Home, Route.Dashboard), navigator.backStack.map { it.route })

        assertTrue(navigator.pop())
        assertEquals(Route.Home, navigator.current.route)
        assertFalse("Home is the bottom of the stack", navigator.pop())

        navigator.navigate(Route.Students)
        navigator.navigate(Route.StudentDetail(3))
        assertTrue(navigator.pop())
        assertTrue(navigator.pop())
        assertEquals(Route.Home, navigator.current.route)
    }

    @Test
    fun `a restored stack always starts at home`() {
        val handle = SavedStateHandle()
        handle["stack"] = arrayListOf("e7|students", "e8|no-such-route")
        handle["next_id"] = 9L

        val navigator = Navigator(handle)

        assertEquals(listOf(Route.Home, Route.Students), navigator.backStack.map { it.route })
        assertEquals(navigator.backStack.map { it.id }.distinct().size, navigator.backStack.size)
    }

    @Test
    fun `replace swaps the top entry`() {
        val navigator = Navigator(SavedStateHandle())
        navigator.navigate(Route.StudentEdit())
        navigator.replace(Route.StudentDetail(12))
        assertEquals(listOf(Route.Home, Route.StudentDetail(12)), navigator.backStack.map { it.route })
    }

    @Test
    fun `back stack is restored from saved state`() {
        val handle = SavedStateHandle()
        val navigator = Navigator(handle)
        navigator.navigate(Route.Invoices)
        navigator.navigate(Route.CreateInvoice(2))

        val restored = Navigator(handle)

        assertEquals(navigator.backStack.map { it.id to it.route }, restored.backStack.map { it.id to it.route })
        restored.navigate(Route.Settings)
        assertEquals(restored.backStack.map { it.id }.distinct().size, restored.backStack.size)
    }
}
