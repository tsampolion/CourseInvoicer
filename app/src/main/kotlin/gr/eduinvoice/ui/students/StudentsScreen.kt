package gr.eduinvoice.ui.students

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.eduinvoice.core.format.Formats
import gr.eduinvoice.core.model.Rate
import gr.eduinvoice.core.model.RateType
import gr.eduinvoice.ui.common.AppIcons
import gr.eduinvoice.ui.common.EmptyState
import gr.eduinvoice.ui.common.LocalFormats
import gr.eduinvoice.ui.common.ScreenScaffold
import gr.eduinvoice.ui.common.appViewModel

@Composable
fun StudentsScreen(
    onOpenStudent: (Long) -> Unit,
    onAddStudent: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentsViewModel = appViewModel { StudentsViewModel(it.studentRepository, it.lessonRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    ScreenScaffold(
        title = "Students",
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddStudent) { Icon(AppIcons.Add, contentDescription = "Add student") }
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
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
            when {
                state.loading -> Unit
                state.totalCount == 0 -> EmptyState("No students yet. Tap + to add your first student.")
                state.rows.isEmpty() -> EmptyState("No matching students.")
                else -> LazyColumn {
                    items(state.rows, key = { it.student.id }) { row ->
                        ListItem(
                            headlineContent = { Text(row.student.fullName) },
                            supportingContent = {
                                Text(
                                    listOf(row.student.className, rateLabel(row.student.rate, formats))
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                )
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
