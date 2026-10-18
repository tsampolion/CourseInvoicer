package gr.edusuite.invoicer.ui.groups

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
import gr.edusuite.invoicer.ui.common.AppIcons
import gr.edusuite.invoicer.ui.common.EmptyState
import gr.edusuite.invoicer.ui.common.LocalFormats
import gr.edusuite.invoicer.ui.common.ScreenScaffold
import gr.edusuite.invoicer.ui.common.ScreenErrorNotice
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.common.plural
import gr.edusuite.invoicer.ui.students.rateLabel

@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    onOpenGroup: (Long) -> Unit,
    onLogLesson: (Long) -> Unit,
    onAddGroup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupsViewModel = appViewModel { GroupsViewModel(it.groupRepository, it.studentRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenErrorNotice(state.message, viewModel::messageShown)
    val formats = LocalFormats.current
    ScreenScaffold(
        title = "Groups",
        modifier = modifier,
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddGroup) { Icon(AppIcons.Add, contentDescription = "Add group") }
        },
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
                state.totalCount == 0 -> EmptyState(
                    icon = AppIcons.Group,
                    title = "No groups yet",
                    message = "Create a group to log one lesson for several students at once.",
                    actionLabel = "Create a group",
                    onAction = onAddGroup,
                )
                state.rows.isEmpty() -> EmptyState(
                    icon = AppIcons.Group,
                    title = "No groups here",
                    message = "Switch between Active and Archived to see the others.",
                )
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
