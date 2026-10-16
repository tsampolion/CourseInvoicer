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
            Route.Home, Route.Students, Route.Groups, Route.Lessons, Route.Invoices, Route.Settings,
            Route.StudentDetail(3), Route.StudentEdit(), Route.StudentEdit(4), Route.GroupEdit(5),
            Route.GroupLesson(6), Route.LessonEdit(7, 8), Route.LessonEdit(studentId = 9), Route.CreateInvoice(10),
        )
        routes.forEach { assertEquals(it, Route.decode(Route.encode(it))) }
        assertNull(Route.decode("student/abc"))
        assertNull(Route.decode("unknown"))
    }

    @Test
    fun `tabs reset the stack and back returns home`() {
        val navigator = Navigator(SavedStateHandle())
        navigator.selectTab(Route.Students)
        navigator.navigate(Route.StudentDetail(3))
        assertEquals(listOf(Route.Home, Route.Students, Route.StudentDetail(3)), navigator.backStack.map { it.route })
        assertEquals(Route.Students, navigator.currentTab)

        navigator.selectTab(Route.Lessons)
        assertEquals(listOf(Route.Home, Route.Lessons), navigator.backStack.map { it.route })

        assertTrue(navigator.pop())
        assertFalse(navigator.pop())
        assertEquals(Route.Home, navigator.current.route)
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
        navigator.selectTab(Route.Invoices)
        navigator.navigate(Route.CreateInvoice(2))

        val restored = Navigator(handle)

        assertEquals(navigator.backStack.map { it.id to it.route }, restored.backStack.map { it.id to it.route })
        restored.navigate(Route.Settings)
        assertEquals(restored.backStack.map { it.id }.distinct().size, restored.backStack.size)
    }
}
