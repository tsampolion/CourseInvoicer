package gr.edusuite.invoicer.ui.students

import android.content.Intent
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
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.ui.common.AppIcons
import gr.edusuite.invoicer.ui.common.ConfirmDialog
import gr.edusuite.invoicer.ui.common.EmptyState
import gr.edusuite.invoicer.ui.common.LocalFormats
import gr.edusuite.invoicer.ui.common.MessageEffect
import gr.edusuite.invoicer.ui.common.ScreenScaffold
import gr.edusuite.invoicer.ui.common.SectionHeader
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.common.plural
import gr.edusuite.invoicer.ui.common.rememberInvoiceSharer
import gr.edusuite.invoicer.ui.common.rememberStudentDataSharer
import gr.edusuite.invoicer.core.privacy.isErased
import gr.edusuite.invoicer.ui.lessons.LessonRow

private enum class PendingAction { Delete, Erase }

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
            settings = it.settingsRepository, messages = it.messages,
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
    val shareData = rememberStudentDataSharer { shareError = it }
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
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    // extraSmall is a pill in the suite theme; a menu takes the panel shape.
                    shape = MaterialTheme.shapes.medium,
                ) {
                    DropdownMenuItem(
                        text = { Text(if (student.archived) "Restore" else "Archive") },
                        onClick = {
                            menuOpen = false
                            viewModel.setArchived(!student.archived)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export this student's data") },
                        onClick = {
                            menuOpen = false
                            viewModel.exportData()?.let { (name, json) -> shareData(name, json) }
                        },
                    )
                    if (!student.isErased) {
                        DropdownMenuItem(
                            text = { Text("Erase student data") },
                            onClick = {
                                menuOpen = false
                                pending = PendingAction.Erase
                            },
                        )
                    }
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
                    // The extended FAB hides its text from accessibility, so the icon carries the label.
                    icon = { Icon(AppIcons.Add, contentDescription = "Log lesson") },
                    text = { Text("Log lesson") },
                )
            }
        },
    ) { padding ->
        if (student == null) {
            if (!state.loading) {
                EmptyState(
                    icon = AppIcons.Person,
                    title = "Student not found",
                    message = "This student no longer exists.",
                    modifier = Modifier.padding(padding),
                )
            }
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
                            Button(onClick = { viewModel.recordPayment() }, enabled = state.openLessons > 0) {
                                Text("Record payment")
                            }
                            OutlinedButton(onClick = onCreateInvoice, enabled = state.invoiceableLessons > 0) {
                                Text("Statement")
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
                item { SectionHeader("Statements") }
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
        PendingAction.Delete -> ConfirmDialog(
            title = "Delete student",
            text = "This cannot be undone. Students with lessons cannot be deleted; archive them instead.",
            confirmLabel = "Delete",
            onConfirm = viewModel::delete,
            onDismiss = { pending = null },
        )
        PendingAction.Erase -> ConfirmDialog(
            title = "Erase student data",
            text = "Removes this student's name and details from the app. Their statements keep only number, " +
                "dates and amounts. Use this when lessons have ended and everything is paid.",
            confirmLabel = "Erase",
            onConfirm = viewModel::eraseData,
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
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${student.phone}".toUri())) }
            }) { Text("Call ${student.phone}") }
        }
        if (student.email.isNotBlank()) {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:${student.email}".toUri())) }
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
