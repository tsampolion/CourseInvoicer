package gr.edusuite.invoicer.ui.lessons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.ui.common.AppIcons
import gr.edusuite.invoicer.ui.common.DateField
import gr.edusuite.invoicer.ui.common.EmptyState
import gr.edusuite.invoicer.ui.common.LocalFormats
import gr.edusuite.invoicer.ui.common.MessageEffect
import gr.edusuite.invoicer.ui.common.MonthSwitcher
import gr.edusuite.invoicer.ui.common.ScreenScaffold
import gr.edusuite.invoicer.ui.common.SectionHeader
import gr.edusuite.invoicer.ui.common.TimeField
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.common.plural
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun LessonsScreen(
    onBack: () -> Unit,
    onOpenLesson: (Long) -> Unit,
    onAddLesson: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LessonsViewModel = appViewModel {
        LessonsViewModel(it.lessonRepository, it.studentRepository, it.groupRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    val snackbar = remember { SnackbarHostState() }
    val selecting = state.selected.isNotEmpty()
    var rescheduling by rememberSaveable { mutableStateOf(false) }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    ScreenScaffold(
        title = if (selecting) "${state.selected.size} selected" else "Lessons",
        modifier = modifier,
        // While lessons are selected the navigation icon clears the selection instead of going back.
        onBack = if (selecting) viewModel::clearSelection else onBack,
        backIcon = if (selecting) AppIcons.Close else AppIcons.Back,
        backLabel = if (selecting) "Clear selection" else "Back",
        snackbarHostState = snackbar,
        actions = {
            if (selecting) {
                TextButton(onClick = viewModel::markSelectedPaid, enabled = state.canMarkPaid) { Text("Mark paid") }
                TextButton(onClick = { rescheduling = true }, enabled = state.canReschedule) { Text("Reschedule") }
            }
        },
        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton(onClick = onAddLesson) { Icon(AppIcons.Add, contentDescription = "Log lesson") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            MonthSwitcher(state.month, viewModel::previousMonth, viewModel::nextMonth, Modifier.padding(horizontal = 8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LessonFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
            if (!state.loading) {
                Text(
                    "${plural(state.items.size, "lesson")} · ${formats.money(state.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            when {
                state.loading -> Unit
                state.items.isEmpty() -> EmptyState(
                    icon = AppIcons.Calendar,
                    title = "No lessons here",
                    message = "Nothing in this month matches the filter. Log a lesson or pick another month.",
                )
                else -> LazyColumn {
                    state.items.groupBy { it.lesson.date }.forEach { (date, dayItems) ->
                        item(key = "day-$date") { SectionHeader(formats.date(date)) }
                        items(dayItems, key = { it.lesson.id }) { item ->
                            LessonRow(
                                lesson = item.lesson,
                                title = item.title,
                                onClick = {
                                    if (selecting) viewModel.toggleSelection(item.lesson.id) else onOpenLesson(item.lesson.id)
                                },
                                onPaidChange = { viewModel.setPaid(item.lesson, it) },
                                selected = item.lesson.id in state.selected,
                                onLongClick = { viewModel.toggleSelection(item.lesson.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    val first = state.firstSelected
    if (rescheduling && first != null) {
        RescheduleDialog(
            count = state.selected.size,
            initialDate = first.date,
            initialTime = first.startTime,
            onConfirm = { date, time ->
                rescheduling = false
                viewModel.rescheduleSelected(date, time)
            },
            onDismiss = { rescheduling = false },
        )
    }
}

/** Picks the new date and start time for the selected lessons; length and fee stay the same. */
@Composable
private fun RescheduleDialog(
    count: Int,
    initialDate: LocalDate,
    initialTime: LocalTime,
    onConfirm: (LocalDate, LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var time by rememberSaveable { mutableStateOf(initialTime) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule ${plural(count, "lesson")}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("New date", date, { date = it })
                TimeField("New start time", time, { time = it })
                Text(
                    "Length and fee stay the same. Only unpaid lessons that are on no statement can move.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(date, time) }) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
