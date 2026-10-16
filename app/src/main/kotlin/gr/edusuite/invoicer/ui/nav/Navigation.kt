package gr.edusuite.invoicer.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/** Every destination in the app. Arguments are plain ids so routes survive process death as strings. */
sealed interface Route {
    data object Home : Route
    data object Students : Route
    data object Groups : Route
    data object Lessons : Route
    data object Invoices : Route
    data object Settings : Route
    data class StudentDetail(val studentId: Long) : Route
    data class StudentEdit(val studentId: Long = 0) : Route
    data class GroupEdit(val groupId: Long = 0) : Route
    data class GroupLesson(val groupId: Long) : Route
    data class LessonEdit(val lessonId: Long = 0, val studentId: Long = 0) : Route
    data class CreateInvoice(val studentId: Long = 0) : Route

    companion object {
        val tabs = listOf(Home, Students, Groups, Lessons, Invoices)

        fun encode(route: Route): String = when (route) {
            Home -> "home"
            Students -> "students"
            Groups -> "groups"
            Lessons -> "lessons"
            Invoices -> "invoices"
            Settings -> "settings"
            is StudentDetail -> "student/${route.studentId}"
            is StudentEdit -> "student-edit/${route.studentId}"
            is GroupEdit -> "group-edit/${route.groupId}"
            is GroupLesson -> "group-lesson/${route.groupId}"
            is LessonEdit -> "lesson-edit/${route.lessonId}/${route.studentId}"
            is CreateInvoice -> "invoice-new/${route.studentId}"
        }

        fun decode(text: String): Route? {
            val parts = text.split('/')
            val ids = parts.drop(1).map { it.toLongOrNull() ?: return null }
            return when (parts.first()) {
                "home" -> Home
                "students" -> Students
                "groups" -> Groups
                "lessons" -> Lessons
                "invoices" -> Invoices
                "settings" -> Settings
                "student" -> ids.getOrNull(0)?.let(::StudentDetail)
                "student-edit" -> ids.getOrNull(0)?.let(::StudentEdit)
                "group-edit" -> ids.getOrNull(0)?.let(::GroupEdit)
                "group-lesson" -> ids.getOrNull(0)?.let(::GroupLesson)
                "lesson-edit" -> if (ids.size == 2) LessonEdit(ids[0], ids[1]) else null
                "invoice-new" -> ids.getOrNull(0)?.let(::CreateInvoice)
                else -> null
            }
        }
    }
}

/**
 * A minimal back stack (no Navigation library). It lives in an Activity-scoped ViewModel so it
 * survives rotation, mirrors itself into [SavedStateHandle] to survive process death, and gives each
 * entry its own [ViewModelStore] that is cleared when the entry is popped.
 */
class Navigator(private val savedState: SavedStateHandle) : ViewModel() {
    class Entry(val id: String, val route: Route)

    private var nextId = savedState.get<Long>(NEXT_ID) ?: 0L
    private val stores = mutableMapOf<String, ViewModelStore>()

    val backStack = mutableStateListOf<Entry>().apply {
        val restored = savedState.get<ArrayList<String>>(STACK).orEmpty().mapNotNull { saved ->
            val id = saved.substringBefore('|')
            Route.decode(saved.substringAfter('|'))?.let { Entry(id, it) }
        }
        addAll(restored.ifEmpty { listOf(newEntry(Route.Home)) })
    }

    val current: Entry get() = backStack.last()

    /** The bottom-bar tab that owns the current screen. */
    val currentTab: Route get() = backStack.lastOrNull { it.route in Route.tabs }?.route ?: Route.Home

    fun navigate(route: Route) {
        backStack += newEntry(route)
        persist()
    }

    /** Replaces the current screen, e.g. "new student" becomes "student detail" after saving. */
    fun replace(route: Route) {
        val removed = backStack.removeAt(backStack.lastIndex)
        stores.remove(removed.id)?.clear()
        navigate(route)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        val removed = backStack.removeAt(backStack.lastIndex)
        stores.remove(removed.id)?.clear()
        persist()
        return true
    }

    /** Switching tabs resets the stack to Home (+ the tab), so Back from any tab returns Home. */
    fun selectTab(tab: Route) {
        val target = if (tab == Route.Home) listOf(Route.Home) else listOf(Route.Home, tab)
        if (backStack.map { it.route } == target) return
        while (backStack.size > 1) {
            val removed = backStack.removeAt(backStack.lastIndex)
            stores.remove(removed.id)?.clear()
        }
        if (tab != Route.Home) backStack += newEntry(tab)
        persist()
    }

    fun storeFor(entry: Entry): ViewModelStore = stores.getOrPut(entry.id) { ViewModelStore() }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }

    private fun newEntry(route: Route): Entry {
        val entry = Entry("e${nextId++}", route)
        savedState[NEXT_ID] = nextId
        return entry
    }

    private fun persist() {
        savedState[STACK] = ArrayList(backStack.map { "${it.id}|${Route.encode(it.route)}" })
    }

    private companion object {
        const val STACK = "stack"
        const val NEXT_ID = "next_id"
    }
}

/** Shows the top entry with its own ViewModelStore and saveable state; handles system Back. */
@Composable
fun NavHost(navigator: Navigator, content: @Composable (Route) -> Unit) {
    val stateHolder = rememberSaveableStateHolder()
    val entry = navigator.current
    val liveIds = navigator.backStack.map { it.id }.toSet()
    val knownIds = remember { mutableSetOf<String>() }
    DisposableEffect(liveIds) {
        (knownIds - liveIds).forEach(stateHolder::removeState)
        knownIds.clear()
        knownIds += liveIds
        onDispose {}
    }

    BackHandler(enabled = navigator.backStack.size > 1) { navigator.pop() }

    val owner = remember(entry.id) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = navigator.storeFor(entry)
        }
    }
    stateHolder.SaveableStateProvider(entry.id) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            content(entry.route)
        }
    }
}
