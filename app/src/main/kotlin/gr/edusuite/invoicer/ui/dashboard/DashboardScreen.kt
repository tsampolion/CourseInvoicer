package gr.edusuite.invoicer.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.ui.common.AppIcons
import gr.edusuite.invoicer.ui.common.LocalFormats
import gr.edusuite.invoicer.ui.common.MonthSwitcher
import gr.edusuite.invoicer.ui.common.ScreenScaffold
import gr.edusuite.invoicer.ui.common.SectionHeader
import gr.edusuite.invoicer.ui.common.Stat
import gr.edusuite.invoicer.ui.common.StatTileRow
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.common.plural
import gr.edusuite.invoicer.ui.theme.Invoicer
import gr.edusuite.invoicer.ui.theme.Space

@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    onOpenStudent: (Long) -> Unit,
    onAddLesson: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = appViewModel { DashboardViewModel(it.studentRepository, it.lessonRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    ScreenScaffold(
        title = "Dashboard",
        modifier = modifier,
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddLesson,
                // The extended FAB hides its text from accessibility, so the icon carries the label.
                icon = { Icon(AppIcons.Add, contentDescription = "Log lesson") },
                text = { Text("Log lesson") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                MonthSwitcher(state.month, viewModel::previousMonth, viewModel::nextMonth, Modifier.padding(8.dp))
            }
            item {
                val summary = state.summary
                val hours = formats.duration(summary.minutes)
                val colors = Invoicer.colors
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    StatTileRow(
                        listOf(
                            Stat("Earned", formats.money(summary.earned), value = summary.earned.cents, color = colors.tileLilac),
                            Stat(
                                "Lessons",
                                summary.lessonCount.toString(),
                                unit = "· $hours",
                                value = summary.lessonCount.toLong(),
                                color = colors.tileLilac,
                            ),
                        ),
                    )
                    StatTileRow(
                        listOf(
                            Stat("Paid", formats.money(summary.paid), value = summary.paid.cents, color = colors.tileMint),
                            Stat("Unpaid", formats.money(summary.unpaid), value = summary.unpaid.cents, color = colors.tileApricot),
                        ),
                    )
                    StatTileRow(
                        listOf(
                            Stat(
                                "Total outstanding (all time)",
                                formats.money(state.outstanding),
                                value = state.outstanding.cents,
                                color = colors.surfaceContainer,
                                figureColor = if (state.outstanding.isPositive) colors.warn else Color.Unspecified,
                            ),
                        ),
                    )
                }
            }
            item { SectionHeader("Who owes") }
            if (!state.loading && state.debtors.isEmpty()) {
                item {
                    Text(
                        if (state.activeStudents == 0) "Add your first student from Students on the home screen." else "Everyone is paid up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.debtors, key = { it.studentId }) { debtor ->
                ListItem(
                    headlineContent = { Text(debtor.name) },
                    supportingContent = { Text(plural(debtor.lessons, "unpaid lesson")) },
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
