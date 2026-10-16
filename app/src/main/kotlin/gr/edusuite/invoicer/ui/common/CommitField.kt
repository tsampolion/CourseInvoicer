package gr.edusuite.invoicer.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A text field for a value that is saved as the user types.
 *
 * The field owns the text being typed and reports every keystroke through [onValueChange]. It takes
 * [value] back only when that is a change from elsewhere, such as a restored backup: a stored value
 * that is just a normalised copy of the text (`"eur"` saved as `"EUR"`, see [sameValue]) or an
 * older echo of a keystroke never moves the cursor or undoes typing.
 *
 * [inputFilter] runs on each keystroke before the text is shown or reported (e.g. digits only).
 */
@Composable
fun CommitField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    inputFilter: (String) -> String = { it },
    sameValue: (local: String, incoming: String) -> Boolean = { local, incoming -> local.trim() == incoming.trim() },
) {
    var text by remember { mutableStateOf(value) }
    // Values this field reported that have not come back as [value] yet, oldest first. State reaches
    // the screen a frame late, so [value] can briefly be an earlier keystroke; that is not a change.
    val reported = remember { ArrayDeque<String>() }

    LaunchedEffect(value) {
        val echo = reported.indexOf(value)
        if (echo >= 0) {
            repeat(echo + 1) { reported.removeFirst() }
        } else if (!sameValue(text, value)) {
            reported.clear()
            text = value
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val filtered = inputFilter(typed)
            if (filtered != text) {
                text = filtered
                reported.addLast(filtered)
                if (reported.size > MAX_UNCONFIRMED) reported.removeFirst()
                onValueChange(filtered)
            }
        },
        label = { Text(label) },
        isError = isError,
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
    )
}

private const val MAX_UNCONFIRMED = 64
