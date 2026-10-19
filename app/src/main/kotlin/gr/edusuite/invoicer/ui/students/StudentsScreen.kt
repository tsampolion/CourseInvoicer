package gr.edusuite.invoicer.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.core.format.Formats
import gr.edusuite.invoicer.core.model.Rate
import gr.edusuite.invoicer.core.model.RateType
import gr.edusuite.invoicer.ui.common.AppIcons
import gr.edusuite.invoicer.ui.common.ChipTone
import gr.edusuite.invoicer.ui.common.StatusChip
import gr.edusuite.invoicer.ui.common.WarningBanner
import gr.edusuite.invoicer.ui.common.plural
import gr.edusuite.invoicer.ui.common.EmptyState
import gr.edusuite.invoicer.ui.common.LocalFormats
import gr.edusuite.invoicer.ui.common.ScreenScaffold
import gr.edusuite.invoicer.ui.common.ScreenErrorNotice
import gr.edusuite.invoicer.ui.common.appViewModel

@Composable
fun StudentsScreen(
    onBack: () -> Unit,
    onOpenStudent: (Long) -> Unit,
    onAddStudent: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentsViewModel = appViewModel {
        StudentsViewModel(it.studentRepository, it.lessonRepository, it.invoiceRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenErrorNotice(state.message, viewModel::messageShown)
    val formats = LocalFormats.current
    // The field owns its text so typing never waits for the filtered list (or the database) to update.
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.onQueryChange(query) }
    ScreenScaffold(
        title = "Students",
        modifier = modifier,
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddStudent) { Icon(AppIcons.Add, contentDescription = "Add student") }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search name, class, phone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.showArchived,
                    onClick = { viewModel.onShowArchivedChange(false) },
                    label = { Text("Active") },
                )
                FilterChip(
                    selected = state.showArchived,
                    onClick = { viewModel.onShowArchivedChange(true) },
                    label = { Text("Archived") },
                )
            }
            if (state.overdueStudents > 0) {
                val have = if (state.overdueStudents == 1) "has" else "have"
                WarningBanner(
                    "${plural(state.overdueStudents, "student")} $have overdue statements",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                state.loading -> Unit
                state.totalCount == 0 -> EmptyState(
                    icon = AppIcons.Person,
                    title = "No students yet",
                    message = "Add your first student to start logging lessons.",
                    actionLabel = "Add a student",
                    onAction = onAddStudent,
                )
                state.rows.isEmpty() -> EmptyState(
                    icon = AppIcons.Person,
                    title = "No matching students",
                    message = "Try another search, or switch between Active and Archived.",
                )
                else -> LazyColumn {
                    items(state.rows, key = { it.student.id }) { row ->
                        ListItem(
                            headlineContent = { Text(row.student.fullName) },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        listOf(row.student.className, rateLabel(row.student.rate, formats))
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · "),
                                        Modifier.weight(1f, fill = false),
                                    )
                                    if (row.overdue) {
                                        StatusChip("Overdue", ChipTone.Warning, Modifier.padding(start = 8.dp))
                                    }
                                    if (row.dueForErasure) {
                                        StatusChip("Due for erasure", ChipTone.Neutral, Modifier.padding(start = 8.dp))
                                    }
                                }
                            },
                            trailingContent = {
                                if (row.outstanding.isPositive) {
                                    Text(
                                        formats.money(row.outstanding),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onOpenStudent(row.student.id) },
                        )
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

fun rateLabel(rate: Rate, formats: Formats): String = when (rate.type) {
    RateType.HOURLY -> "${formats.money(rate.amount)} / hour"
    RateType.PER_LESSON -> "${formats.money(rate.amount)} / lesson"
}
