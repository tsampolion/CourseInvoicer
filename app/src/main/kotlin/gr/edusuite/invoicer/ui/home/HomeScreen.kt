package gr.edusuite.invoicer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.ui.common.AppIcons
import gr.edusuite.invoicer.ui.common.LocalFormats
import gr.edusuite.invoicer.ui.common.Logo
import gr.edusuite.invoicer.ui.common.Tile
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.common.plural
import gr.edusuite.invoicer.ui.theme.Invoicer
import gr.edusuite.invoicer.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The launcher: the EduSuite home header, then a bento grid with one tile per section and a live subtitle
 * (guide §6). A section that has nothing yet shows its tile dimmed, and the tile still opens it so the first
 * item can be added.
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

    val colors = Invoicer.colors
    // Three tile hues at most on one screen (guide §2.2): sky, lilac and apricot; Settings is neutral.
    val dashboard: @Composable (Modifier) -> Unit = { tileModifier ->
        Tile(
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
            color = colors.tileSky,
            modifier = tileModifier,
        )
    }
    val students: @Composable (Modifier) -> Unit = { tileModifier ->
        Tile(
            title = "Students",
            subtitle = subtitle {
                if (state.studentsEmpty) "Add your first student" else plural(state.activeStudents, "student")
            },
            icon = AppIcons.Person,
            onClick = onOpenStudents,
            empty = state.studentsEmpty,
            color = colors.tileSky,
            modifier = tileModifier,
        )
    }
    val groups: @Composable (Modifier) -> Unit = { tileModifier ->
        Tile(
            title = "Groups",
            subtitle = subtitle {
                if (state.groupsEmpty) "One lesson for several students" else plural(state.activeGroups, "group")
            },
            icon = AppIcons.Group,
            onClick = onOpenGroups,
            empty = state.groupsEmpty,
            color = colors.tileLilac,
            modifier = tileModifier,
        )
    }
    val lessons: @Composable (Modifier) -> Unit = { tileModifier ->
        Tile(
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
            color = colors.tileLilac,
            modifier = tileModifier,
        )
    }
    val statements: @Composable (Modifier) -> Unit = { tileModifier ->
        Tile(
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
            color = colors.tileApricot,
            modifier = tileModifier,
        )
    }
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (state.canLogLesson) {
                ExtendedFloatingActionButton(
                    onClick = onLogLesson,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // Flat, like the rest of the suite (guide §4).
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    // The extended FAB hides its text from accessibility, so the icon carries the label.
                    icon = { Icon(AppIcons.Add, contentDescription = "Log lesson") },
                    text = { Text("Log lesson") },
                )
            }
        },
    ) { padding ->
        // A scrolling Column, not a lazy grid, so every tile is composed and performScrollTo reaches it.
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
            ) {
                Spacer(Modifier.height(Space.xxl))
                // Slot for the damaged-database banner: a one-line DamagedDatabaseBanner(…) call
                // (ui/home/DamagedDatabaseBanner.kt) goes here when it lands, above the header.
                if (state.databaseDamaged) DamagedDatabaseBanner(onOpenSettings, viewModel::acknowledgeDamagedDatabase)
                HomeHeader(onOpenSettings)
                Spacer(Modifier.height(Space.xl))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // The bento becomes one column at 200% font scale or under 360dp width (guide §6).
                    val oneColumn = LocalDensity.current.fontScale >= 2f || maxWidth < 360.dp
                    if (oneColumn) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                            val full = Modifier.fillMaxWidth()
                            lessons(full)
                            statements(full)
                            students(full)
                            dashboard(full)
                            groups(full)
                        }
                    } else {
                        // A two-column bento: the most-used sections are tall, the rest short.
                        val tall = Modifier.fillMaxWidth().heightIn(min = 184.dp)
                        val short = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                                lessons(tall)
                                students(short)
                                groups(short)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                                statements(tall)
                                dashboard(tall)
                            }
                        }
                    }
                }
                // Room for the floating button, so it never covers the last tile.
                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

/**
 * The suite home header (guide §6): the 48dp logo, the greeting over today's date, and a pill Settings
 * button at the end (40dp visible, 48dp touch target).
 */
@Composable
private fun HomeHeader(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalFormats.current.locale
    val today = remember(locale) {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Logo(size = 48.dp)
        Column(Modifier.weight(1f)) {
            Text(
                "Welcome to Course Invoicer",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(today, style = MaterialTheme.typography.bodyMedium, color = Invoicer.colors.muted)
        }
        IconButton(
            onClick = onOpenSettings,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Invoicer.colors.surface,
                contentColor = Invoicer.colors.ink,
            ),
        ) { Icon(AppIcons.Settings, contentDescription = "Open settings") }
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
