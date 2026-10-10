package gr.eduinvoice.ui.lessons

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.eduinvoice.ui.common.AppIcons
import gr.eduinvoice.ui.common.EmptyState
import gr.eduinvoice.ui.common.LocalFormats
import gr.eduinvoice.ui.common.MessageEffect
import gr.eduinvoice.ui.common.MonthSwitcher
import gr.eduinvoice.ui.common.ScreenScaffold
import gr.eduinvoice.ui.common.SectionHeader
import gr.eduinvoice.ui.common.appViewModel
import gr.eduinvoice.ui.common.plural

@Composable
fun LessonsScreen(
    onOpenLesson: (Long) -> Unit,
    onAddLesson: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LessonsViewModel = appViewModel {
        LessonsViewModel(it.lessonRepository, it.studentRepository, it.groupRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)

    ScreenScaffold(
        title = "Lessons",
        modifier = modifier,
        snackbarHostState = snackbar,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddLesson) { Icon(AppIcons.Add, contentDescription = "Log lesson") }
        },
        bottomBar = bottomBar,
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
                state.items.isEmpty() -> EmptyState("No lessons in this month.")
                else -> LazyColumn {
                    state.items.groupBy { it.lesson.date }.forEach { (date, dayItems) ->
                        item(key = "day-$date") { SectionHeader(formats.date(date)) }
                        items(dayItems, key = { it.lesson.id }) { item ->
                            LessonRow(
                                lesson = item.lesson,
                                title = item.title,
                                onClick = { onOpenLesson(item.lesson.id) },
                                onPaidChange = { viewModel.setPaid(item.lesson, it) },
                            )
                            HorizontalDivider()
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}
