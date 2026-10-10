package gr.eduinvoice.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.eduinvoice.ui.common.AppIcons
import gr.eduinvoice.ui.common.LocalFormats
import gr.eduinvoice.ui.common.MetricCard
import gr.eduinvoice.ui.common.MonthSwitcher
import gr.eduinvoice.ui.common.ScreenScaffold
import gr.eduinvoice.ui.common.SectionHeader
import gr.eduinvoice.ui.common.appViewModel

@Composable
fun HomeScreen(
    onOpenStudent: (Long) -> Unit,
    onAddLesson: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = appViewModel { HomeViewModel(it.studentRepository, it.lessonRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    ScreenScaffold(
        title = "EduInvoice",
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(AppIcons.Settings, contentDescription = "Settings") }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddLesson,
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text("Log lesson") },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                MonthSwitcher(state.month, viewModel::previousMonth, viewModel::nextMonth, Modifier.padding(8.dp))
            }
            item {
                val summary = state.summary
                val hours = formats.duration(summary.minutes)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricCard("Earned", formats.money(summary.earned), Modifier.weight(1f))
                    MetricCard("Lessons", "${summary.lessonCount} · $hours", Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricCard(
                        "Paid",
                        formats.money(summary.paid),
                        Modifier.weight(1f),
                        valueColor = MaterialTheme.colorScheme.tertiary,
                    )
                    MetricCard("Unpaid", formats.money(summary.unpaid), Modifier.weight(1f))
                }
            }
            item {
                MetricCard(
                    "Total outstanding (all time)",
                    formats.money(state.outstanding),
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    valueColor = if (state.outstanding.isPositive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            item { SectionHeader("Who owes") }
            if (!state.loading && state.debtors.isEmpty()) {
                item {
                    Text(
                        if (state.activeStudents == 0) "Add your first student in the Students tab." else "Everyone is paid up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.debtors, key = { it.studentId }) { debtor ->
                ListItem(
                    headlineContent = { Text(debtor.name) },
                    supportingContent = { Text("${debtor.lessons} unpaid lesson" + if (debtor.lessons == 1) "" else "s") },
                    trailingContent = {
                        Text(
                            formats.money(debtor.amount),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.clickable { onOpenStudent(debtor.studentId) },
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}
