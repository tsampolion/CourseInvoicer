package gr.edusuite.invoicer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.common.ScreenErrorNotice
import gr.edusuite.invoicer.ui.home.HomeViewModel
import gr.edusuite.invoicer.ui.dashboard.DashboardViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import gr.edusuite.invoicer.ui.dashboard.DashboardScreen
import gr.edusuite.invoicer.ui.groups.GroupEditScreen
import gr.edusuite.invoicer.ui.groups.GroupLessonScreen
import gr.edusuite.invoicer.ui.groups.GroupsScreen
import gr.edusuite.invoicer.ui.home.HomeScreen
import gr.edusuite.invoicer.ui.invoices.CreateInvoiceScreen
import gr.edusuite.invoicer.ui.invoices.InvoicesScreen
import gr.edusuite.invoicer.ui.lessons.LessonEditScreen
import gr.edusuite.invoicer.ui.lessons.LessonsScreen
import gr.edusuite.invoicer.ui.nav.NavHost
import gr.edusuite.invoicer.ui.nav.Navigator
import gr.edusuite.invoicer.ui.nav.Route
import gr.edusuite.invoicer.ui.settings.SettingsScreen
import gr.edusuite.invoicer.ui.students.StudentDetailScreen
import gr.edusuite.invoicer.ui.students.StudentEditScreen
import gr.edusuite.invoicer.ui.students.StudentsScreen

/** Dispatches each route to its screen. Home is the launcher; every other screen has a Back arrow. */
@Composable
fun CourseInvoicerApp() {
    val navigator: Navigator = viewModel { Navigator(createSavedStateHandle()) }
    val back: () -> Unit = { navigator.pop() }

    NavHost(navigator) { route ->
        when (route) {
            Route.Home -> {
                val vm = appViewModel {
                    HomeViewModel(
                        it.studentRepository, it.groupRepository, it.lessonRepository,
                        it.invoiceRepository, it.settingsRepository, it.reminderStateRepository,
                        databaseDamaged = it.databaseHealth.damaged,
                        acknowledgeDamage = it.databaseHealth::acknowledge,
                    )
                }
                val state by vm.state.collectAsStateWithLifecycle()
                HomeScreen(
                    viewModel = vm,
                    onOpenDashboard = { navigator.navigate(Route.Dashboard) },
                    onOpenStudents = { navigator.navigate(Route.Students) },
                    onOpenGroups = { navigator.navigate(Route.Groups) },
                    onOpenLessons = { navigator.navigate(Route.Lessons) },
                    onOpenStatements = { navigator.navigate(Route.Invoices) },
                    onOpenSettings = { navigator.navigate(Route.Settings) },
                    onLogLesson = { navigator.navigate(Route.LessonEdit()) },
                )
                ScreenErrorNotice(state.message, vm::messageShown)
            }
            Route.Dashboard -> {
                val vm = appViewModel { DashboardViewModel(it.studentRepository, it.lessonRepository) }
                val state by vm.state.collectAsStateWithLifecycle()
                DashboardScreen(
                    viewModel = vm,
                    onBack = back,
                    onOpenStudent = { navigator.navigate(Route.StudentDetail(it)) },
                    onAddLesson = { navigator.navigate(Route.LessonEdit()) },
                )
                ScreenErrorNotice(state.message, vm::messageShown)
            }
            Route.Students -> StudentsScreen(
                onBack = back,
                onOpenStudent = { navigator.navigate(Route.StudentDetail(it)) },
                onAddStudent = { navigator.navigate(Route.StudentEdit()) },
            )
            Route.Groups -> GroupsScreen(
                onBack = back,
                onOpenGroup = { navigator.navigate(Route.GroupEdit(it)) },
                onLogLesson = { navigator.navigate(Route.GroupLesson(it)) },
                onAddGroup = { navigator.navigate(Route.GroupEdit()) },
            )
            Route.Lessons -> LessonsScreen(
                onBack = back,
                onOpenLesson = { navigator.navigate(Route.LessonEdit(lessonId = it)) },
                onAddLesson = { navigator.navigate(Route.LessonEdit()) },
            )
            Route.Invoices -> InvoicesScreen(
                onBack = back,
                onCreateInvoice = { navigator.navigate(Route.CreateInvoice()) },
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
