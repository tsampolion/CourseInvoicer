package gr.eduinvoice.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.eduinvoice.ui.common.AppIcons
import gr.eduinvoice.ui.common.EmptyState
import gr.eduinvoice.ui.common.LocalFormats
import gr.eduinvoice.ui.common.ScreenScaffold
import gr.eduinvoice.ui.common.appViewModel
import gr.eduinvoice.ui.common.plural
import gr.eduinvoice.ui.students.rateLabel

@Composable
fun GroupsScreen(
    onOpenGroup: (Long) -> Unit,
    onLogLesson: (Long) -> Unit,
    onAddGroup: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupsViewModel = appViewModel { GroupsViewModel(it.groupRepository, it.studentRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    ScreenScaffold(
        title = "Groups",
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddGroup) { Icon(AppIcons.Add, contentDescription = "Add group") }
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(Modifier.padding(padding)) {
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
                state.totalCount == 0 -> EmptyState("No groups yet. Create one to log lessons for several students at once.")
                state.rows.isEmpty() -> EmptyState("No groups here.")
                else -> LazyColumn {
                    items(state.rows, key = { it.group.id }) { row ->
                        ListItem(
                            headlineContent = { Text(row.group.name) },
                            supportingContent = {
                                Text(
                                    listOf(
                                        row.group.className,
                                        plural(row.activeMembers, "student"),
                                        rateLabel(row.group.rate, formats),
                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                )
                            },
                            trailingContent = {
                                if (!row.group.archived) {
                                    TextButton(
                                        onClick = { onLogLesson(row.group.id) },
                                        enabled = row.activeMembers > 0,
                                    ) { Text("Log lesson") }
                                }
                            },
                            modifier = Modifier.clickable { onOpenGroup(row.group.id) },
                        )
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}
