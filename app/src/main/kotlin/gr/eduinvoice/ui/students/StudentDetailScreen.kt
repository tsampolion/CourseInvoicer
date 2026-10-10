package gr.eduinvoice.ui.students

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.ui.common.AppIcons
import gr.eduinvoice.ui.common.ConfirmDialog
import gr.eduinvoice.ui.common.EmptyState
import gr.eduinvoice.ui.common.LocalFormats
import gr.eduinvoice.ui.common.MessageEffect
import gr.eduinvoice.ui.common.ScreenScaffold
import gr.eduinvoice.ui.common.SectionHeader
import gr.eduinvoice.ui.common.appViewModel
import gr.eduinvoice.ui.common.plural
import gr.eduinvoice.ui.common.rememberInvoiceSharer
import gr.eduinvoice.ui.lessons.LessonRow

private enum class PendingAction { Payment, Archive, Delete }

@Composable
fun StudentDetailScreen(
    studentId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenLesson: (Long) -> Unit,
    onAddLesson: () -> Unit,
    onCreateInvoice: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentDetailViewModel = appViewModel {
        StudentDetailViewModel(
            studentId = studentId,
            students = it.studentRepository,
            groups = it.groupRepository,
            lessons = it.lessonRepository,
            invoices = it.invoiceRepository,
            settings = it.settingsRepository,
        )
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<PendingAction?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val shareInvoice = rememberInvoiceSharer { shareError = it }
    val student = state.student

    MessageEffect(state.message, snackbar, viewModel::messageShown)
    MessageEffect(shareError, snackbar) { shareError = null }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    ScreenScaffold(
        title = student?.fullName.orEmpty(),
        modifier = modifier,
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (student != null) {
                IconButton(onClick = onEdit) { Icon(AppIcons.Edit, contentDescription = "Edit") }
                IconButton(onClick = { menuOpen = true }) { Icon(AppIcons.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (student.archived) "Restore" else "Archive") },
                        onClick = {
                            menuOpen = false
                            if (student.archived) viewModel.setArchived(false) else pending = PendingAction.Archive
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuOpen = false
                            pending = PendingAction.Delete
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (student != null && !student.archived) {
                ExtendedFloatingActionButton(
                    onClick = onAddLesson,
                    icon = { Icon(AppIcons.Add, contentDescription = null) },
                    text = { Text("Log lesson") },
                )
            }
        },
    ) { padding ->
        if (student == null) {
            if (!state.loading) EmptyState("This student no longer exists.", Modifier.padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Outstanding", style = MaterialTheme.typography.labelMedium)
                        Text(
                            formats.money(state.outstanding),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (state.outstanding.isPositive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(plural(state.openLessons, "unpaid lesson"), style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pending = PendingAction.Payment }, enabled = state.openLessons > 0) {
                                Text("Record payment")
                            }
                            OutlinedButton(onClick = onCreateInvoice, enabled = state.invoiceableLessons > 0) {
                                Text("Invoice")
                            }
                        }
                    }
                }
            }
            item { StudentDetails(student) }
            item { SectionHeader("Lessons (${state.lessons.size})") }
            if (state.lessons.isEmpty()) {
                item { Text("No lessons yet.", Modifier.padding(horizontal = 16.dp)) }
            }
            items(state.lessons, key = { "lesson-${it.id}" }) { lesson ->
                LessonRow(
                    lesson = lesson,
                    title = lesson.groupId?.let { state.groupNames[it] }?.let { "Group · $it" } ?: "Lesson",
                    onClick = { onOpenLesson(lesson.id) },
                    onPaidChange = { viewModel.setPaid(lesson, it) },
                )
                HorizontalDivider()
            }
            if (state.invoices.isNotEmpty()) {
                item { SectionHeader("Invoices") }
                items(state.invoices, key = { "invoice-${it.id}" }) { invoice ->
                    ListItem(
                        headlineContent = { Text(invoice.number + if (invoice.isVoid) " (void)" else "") },
                        supportingContent = { Text(formats.date(invoice.issueDate)) },
                        trailingContent = { Text(formats.money(invoice.total)) },
                        leadingContent = {
                            if (!invoice.isVoid) Icon(AppIcons.Share, contentDescription = "Share")
                        },
                        modifier = Modifier.clickable(enabled = !invoice.isVoid) { shareInvoice(invoice.id) },
                    )
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    when (pending) {
        PendingAction.Payment -> ConfirmDialog(
            title = "Record payment",
            text = "Mark ${plural(state.openLessons, "unpaid lesson")} (${formats.money(state.outstanding)}) as paid today?",
            confirmLabel = "Mark paid",
            onConfirm = viewModel::recordPayment,
            onDismiss = { pending = null },
        )
        PendingAction.Archive -> ConfirmDialog(
            title = "Archive student",
            text = "Archived students are hidden from lists and pickers. Their lessons and invoices are kept.",
            confirmLabel = "Archive",
            onConfirm = { viewModel.setArchived(true) },
            onDismiss = { pending = null },
        )
        PendingAction.Delete -> ConfirmDialog(
            title = "Delete student",
            text = "This cannot be undone. Students with lessons cannot be deleted; archive them instead.",
            confirmLabel = "Delete",
            onConfirm = viewModel::delete,
            onDismiss = { pending = null },
        )
        null -> Unit
    }
}

@Composable
private fun StudentDetails(student: Student, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val formats = LocalFormats.current
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(rateLabel(student.rate, formats), style = MaterialTheme.typography.bodyLarge)
        if (student.className.isNotBlank()) Text("Class: ${student.className}")
        if (student.guardianName.isNotBlank()) Text("Parent/guardian: ${student.guardianName}")
        if (student.phone.isNotBlank()) {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.phone}"))) }
            }) { Text("Call ${student.phone}") }
        }
        if (student.email.isNotBlank()) {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${student.email}"))) }
            }) { Text("Email ${student.email}") }
        }
        if (student.notes.isNotBlank()) {
            Text(student.notes, style = MaterialTheme.typography.bodyMedium)
        }
        if (student.archived) {
            Text("Archived", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
        }
    }
}
