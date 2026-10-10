package gr.eduinvoice.ui.students

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.eduinvoice.core.model.RateType
import gr.eduinvoice.ui.common.DecimalField
import gr.eduinvoice.ui.common.FormSpacing
import gr.eduinvoice.ui.common.LocalFormats
import gr.eduinvoice.ui.common.MessageEffect
import gr.eduinvoice.ui.common.ScreenScaffold
import gr.eduinvoice.ui.common.appViewModel

@Composable
fun StudentEditScreen(
    studentId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentEditViewModel = appViewModel { StudentEditViewModel(studentId, it.studentRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val form = state.form
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }

    ScreenScaffold(
        title = if (state.isNew) "New student" else "Edit student",
        modifier = modifier,
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            TextButton(onClick = viewModel::save, enabled = !state.loading && !state.saving) { Text("Save") }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = FormSpacing,
        ) {
            TextInput("First name *", form.firstName, { v -> viewModel.update { it.copy(firstName = v) } },
                isError = state.firstNameError, error = "Required", capitalize = true)
            TextInput("Last name", form.lastName, { v -> viewModel.update { it.copy(lastName = v) } }, capitalize = true)
            TextInput("Class / level", form.className, { v -> viewModel.update { it.copy(className = v) } })
            val suggestions = state.classSuggestions.filter { it != form.className }
            if (suggestions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.update { it.copy(className = suggestion) } },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }
            TextInput("Parent / guardian", form.guardianName, { v -> viewModel.update { it.copy(guardianName = v) } }, capitalize = true)
            TextInput("Phone", form.phone, { v -> viewModel.update { it.copy(phone = v) } }, keyboardType = KeyboardType.Phone)
            TextInput("Email", form.email, { v -> viewModel.update { it.copy(email = v) } }, keyboardType = KeyboardType.Email)
            RateInput(
                amount = form.rate,
                type = form.rateType,
                onAmountChange = { v -> viewModel.update { it.copy(rate = v) } },
                onTypeChange = { v -> viewModel.update { it.copy(rateType = v) } },
                isError = state.rateError,
            )
            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun TextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    error: String? = null,
    capitalize: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        supportingText = if (isError && error != null) {
            { Text(error) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            keyboardType = keyboardType,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Rate amount plus "per hour" / "per lesson" choice; shared by students and groups. */
@Composable
internal fun RateInput(
    amount: String,
    type: RateType,
    onAmountChange: (String) -> Unit,
    onTypeChange: (RateType) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = type == RateType.HOURLY,
                onClick = { onTypeChange(RateType.HOURLY) },
                label = { Text("Per hour") },
            )
            FilterChip(
                selected = type == RateType.PER_LESSON,
                onClick = { onTypeChange(RateType.PER_LESSON) },
                label = { Text("Per lesson") },
            )
        }
        DecimalField(
            label = if (type == RateType.HOURLY) "Hourly rate *" else "Fee per lesson *",
            value = amount,
            onValueChange = onAmountChange,
            prefix = LocalFormats.current.currencySymbol,
            isError = isError,
            supportingText = if (isError) "Enter an amount, e.g. 20 or 17.50" else null,
        )
    }
}
