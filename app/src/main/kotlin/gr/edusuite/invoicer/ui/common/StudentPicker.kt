package gr.edusuite.invoicer.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gr.edusuite.invoicer.core.model.Student

/** Picker field + searchable dialog for choosing a student. */
@Composable
fun StudentPickerField(
    students: List<Student>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selected = students.firstOrNull { it.id == selectedId }
    PickerField(
        label = "Student *",
        value = selected?.fullName.orEmpty(),
        onClick = { open = true },
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        supportingText = if (isError) "Choose a student" else null,
    )
    if (open) {
        var query by rememberSaveable { mutableStateOf("") }
        val matches = students.filter { it.fullName.contains(query.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Choose student") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(matches, key = { it.id }) { student ->
                            ListItem(
                                headlineContent = { Text(student.fullName) },
                                supportingContent = student.className.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                                modifier = Modifier.clickable {
                                    onSelect(student.id)
                                    open = false
                                },
                            )
                        }
                    }
                    if (matches.isEmpty()) Text("No students found.")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}
