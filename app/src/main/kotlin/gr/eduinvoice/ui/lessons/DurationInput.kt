package gr.eduinvoice.ui.lessons

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import gr.eduinvoice.core.usecase.MAX_LESSON_MINUTES

private val PRESETS = listOf(30, 45, 60, 90, 120)

@Composable
fun DurationInput(
    minutes: String,
    onMinutesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { preset ->
                FilterChip(
                    selected = minutes == preset.toString(),
                    onClick = { onMinutesChange(preset.toString()) },
                    label = { Text("$preset min") },
                    enabled = enabled,
                )
            }
        }
        OutlinedTextField(
            value = minutes,
            onValueChange = onMinutesChange,
            label = { Text("Duration (minutes) *") },
            isError = isError,
            supportingText = if (isError) {
                { Text("Between 1 and $MAX_LESSON_MINUTES minutes") }
            } else {
                null
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
