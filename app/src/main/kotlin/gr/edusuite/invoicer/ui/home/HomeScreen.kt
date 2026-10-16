package gr.edusuite.invoicer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.ui.common.AppIcons
import gr.edusuite.invoicer.ui.common.HomeButton
import gr.edusuite.invoicer.ui.common.LocalFormats
import gr.edusuite.invoicer.ui.common.Logo
import gr.edusuite.invoicer.ui.common.SuiteTone
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.common.plural
import gr.edusuite.invoicer.ui.theme.Invoicer
import java.time.format.DateTimeFormatter

/**
 * The launcher: the EduSuite home header, then one button per section with a live subtitle. A section that
 * has nothing yet shows its button dimmed, and the button still opens it so the first item can be added.
 */
@Composable
fun HomeScreen(
    onOpenDashboard: () -> Unit,
    onOpenStudents: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenLessons: () -> Unit,
    onOpenStatements: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogLesson: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = appViewModel {
        HomeViewModel(
            it.studentRepository,
            it.groupRepository,
            it.lessonRepository,
            it.invoiceRepository,
            it.settingsRepository,
            it.reminderStateRepository,
        )
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    state.reminder?.let { reminder ->
        ReminderDialog(
            reminder = reminder,
            onCreateStatements = {
                viewModel.dismissReminder()
                onOpenStatements()
            },
            onRemindTomorrow = viewModel::remindTomorrow,
            onDone = viewModel::doneForMonth,
            onDismiss = viewModel::dismissReminder,
        )
    }
    // Until the counts load, subtitles stay blank rather than showing zeros.
    fun subtitle(text: () -> String): String = if (state.loading) "" else text()

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (state.canLogLesson) {
                ExtendedFloatingActionButton(
                    onClick = onLogLesson,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // The extended FAB hides its text from accessibility, so the icon carries the label.
                    icon = { Icon(AppIcons.Add, contentDescription = "Log lesson") },
                    text = { Text("Log lesson") },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(40.dp))
                Logo()
                Spacer(Modifier.height(16.dp))
                Text(
                    "Welcome to Course Invoicer",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "part of EduSuite apps",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = Invoicer.colors.muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeButton(
                        title = "Dashboard",
                        subtitle = subtitle {
                            when {
                                state.outstanding.isPositive -> "${formats.money(state.outstanding)} outstanding"
                                state.lessonsEmpty -> "Earnings and who owes"
                                else -> "Everyone is paid up"
                            }
                        },
                        icon = AppIcons.Dashboard,
                        onClick = onOpenDashboard,
                    )
                    HomeButton(
                        title = "Students",
                        subtitle = subtitle {
                            if (state.studentsEmpty) {
                                "Add your first student"
                            } else {
                                plural(state.activeStudents, "student")
                            }
                        },
                        icon = AppIcons.Person,
                        onClick = onOpenStudents,
                        empty = state.studentsEmpty,
                    )
                    HomeButton(
                        title = "Groups",
                        subtitle = subtitle {
                            if (state.groupsEmpty) {
                                "One lesson for several students"
                            } else {
                                plural(state.activeGroups, "group")
                            }
                        },
                        icon = AppIcons.Group,
                        onClick = onOpenGroups,
                        empty = state.groupsEmpty,
                    )
                    HomeButton(
                        title = "Lessons",
                        subtitle = subtitle {
                            when {
                                state.lessonsEmpty -> "Log your first lesson"
                                state.uninvoicedLessons == 0 -> "All invoiced"
                                else -> "${state.uninvoicedLessons} not invoiced"
                            }
                        },
                        icon = AppIcons.Calendar,
                        onClick = onOpenLessons,
                        empty = state.lessonsEmpty,
                    )
                    HomeButton(
                        title = "Statements",
                        subtitle = subtitle {
                            when {
                                state.statementsEmpty -> "Bill a student's lessons"
                                state.unpaidStatements == 0 -> "All paid"
                                else -> "${state.unpaidStatements} unpaid"
                            }
                        },
                        icon = AppIcons.Invoice,
                        onClick = onOpenStatements,
                        empty = state.statementsEmpty,
                    )
                    HomeButton(
                        title = "Settings",
                        subtitle = "Your details, theme and backup",
                        icon = AppIcons.Settings,
                        onClick = onOpenSettings,
                        tone = SuiteTone.Neutral,
                    )
                }
                // Room for the floating button, so it never covers the last button.
                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

/** The month-end reminder (R15). Back or a tap outside only hides it; the buttons answer it. */
@Composable
private fun ReminderDialog(
    reminder: ReminderPrompt,
    onCreateStatements: () -> Unit,
    onRemindTomorrow: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalFormats.current.locale
    val monthName = remember(reminder.month, locale) {
        reminder.month.format(DateTimeFormatter.ofPattern("LLLL", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time to invoice $monthName") },
        text = {
            Text(
                "${plural(reminder.lessons, "lesson")} for ${plural(reminder.students, "student")} " +
                    (if (reminder.lessons == 1) "isn't" else "aren't") + " on a statement yet.",
            )
        },
        confirmButton = { TextButton(onClick = onCreateStatements) { Text("Create statements") } },
        // The dialog lays its buttons out in a row that wraps, so all three fit on a narrow phone.
        dismissButton = {
            TextButton(onClick = onDone) { Text("Done for this month") }
            TextButton(onClick = onRemindTomorrow) { Text("Remind me tomorrow") }
        },
    )
}
