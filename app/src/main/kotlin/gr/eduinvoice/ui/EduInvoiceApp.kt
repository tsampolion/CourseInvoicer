package gr.eduinvoice.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import gr.eduinvoice.ui.common.AppIcons
import gr.eduinvoice.ui.groups.GroupEditScreen
import gr.eduinvoice.ui.groups.GroupLessonScreen
import gr.eduinvoice.ui.groups.GroupsScreen
import gr.eduinvoice.ui.home.HomeScreen
import gr.eduinvoice.ui.invoices.CreateInvoiceScreen
import gr.eduinvoice.ui.invoices.InvoicesScreen
import gr.eduinvoice.ui.lessons.LessonEditScreen
import gr.eduinvoice.ui.lessons.LessonsScreen
import gr.eduinvoice.ui.nav.NavHost
import gr.eduinvoice.ui.nav.Navigator
import gr.eduinvoice.ui.nav.Route
import gr.eduinvoice.ui.settings.SettingsScreen
import gr.eduinvoice.ui.students.StudentDetailScreen
import gr.eduinvoice.ui.students.StudentEditScreen
import gr.eduinvoice.ui.students.StudentsScreen

@Composable
fun EduInvoiceApp() {
    val navigator: Navigator = viewModel { Navigator(createSavedStateHandle()) }
    val bottomBar: @Composable () -> Unit = {
        AppBottomBar(current = navigator.currentTab, onSelect = navigator::selectTab)
    }
    val back: () -> Unit = { navigator.pop() }

    NavHost(navigator) { route ->
        when (route) {
            Route.Home -> HomeScreen(
                onOpenStudent = { navigator.navigate(Route.StudentDetail(it)) },
                onAddLesson = { navigator.navigate(Route.LessonEdit()) },
                onOpenSettings = { navigator.navigate(Route.Settings) },
                bottomBar = bottomBar,
            )
            Route.Students -> StudentsScreen(
                onOpenStudent = { navigator.navigate(Route.StudentDetail(it)) },
                onAddStudent = { navigator.navigate(Route.StudentEdit()) },
                bottomBar = bottomBar,
            )
            Route.Groups -> GroupsScreen(
                onOpenGroup = { navigator.navigate(Route.GroupEdit(it)) },
                onLogLesson = { navigator.navigate(Route.GroupLesson(it)) },
                onAddGroup = { navigator.navigate(Route.GroupEdit()) },
                bottomBar = bottomBar,
            )
            Route.Lessons -> LessonsScreen(
                onOpenLesson = { navigator.navigate(Route.LessonEdit(lessonId = it)) },
                onAddLesson = { navigator.navigate(Route.LessonEdit()) },
                bottomBar = bottomBar,
            )
            Route.Invoices -> InvoicesScreen(
                onCreateInvoice = { navigator.navigate(Route.CreateInvoice()) },
                bottomBar = bottomBar,
            )
            Route.Settings -> SettingsScreen(onBack = back)
            is Route.StudentDetail -> StudentDetailScreen(
                studentId = route.studentId,
                onBack = back,
                onEdit = { navigator.navigate(Route.StudentEdit(route.studentId)) },
                onOpenLesson = { navigator.navigate(Route.LessonEdit(lessonId = it)) },
                onAddLesson = { navigator.navigate(Route.LessonEdit(studentId = route.studentId)) },
                onCreateInvoice = { navigator.navigate(Route.CreateInvoice(route.studentId)) },
            )
            is Route.StudentEdit -> StudentEditScreen(
                studentId = route.studentId,
                onBack = back,
                onSaved = { id ->
                    if (route.studentId == 0L) navigator.replace(Route.StudentDetail(id)) else navigator.pop()
                },
            )
            is Route.GroupEdit -> GroupEditScreen(groupId = route.groupId, onBack = back, onDone = back)
            is Route.GroupLesson -> GroupLessonScreen(groupId = route.groupId, onBack = back, onDone = back)
            is Route.LessonEdit -> LessonEditScreen(
                lessonId = route.lessonId,
                studentId = route.studentId,
                onBack = back,
                onDone = back,
            )
            is Route.CreateInvoice -> CreateInvoiceScreen(studentId = route.studentId, onBack = back, onDone = back)
        }
    }
}

@Composable
private fun AppBottomBar(current: Route, onSelect: (Route) -> Unit) {
    NavigationBar {
        Route.tabs.forEach { tab ->
            val (label, icon) = when (tab) {
                Route.Home -> "Home" to AppIcons.Home
                Route.Students -> "Students" to AppIcons.Person
                Route.Groups -> "Groups" to AppIcons.Group
                Route.Lessons -> "Lessons" to AppIcons.Calendar
                else -> "Invoices" to AppIcons.Invoice
            }
            NavigationBarItem(
                selected = current == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}
