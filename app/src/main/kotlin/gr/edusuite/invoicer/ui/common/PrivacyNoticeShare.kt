package gr.edusuite.invoicer.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import gr.edusuite.invoicer.core.privacy.NoticeLanguage

/**
 * *Share privacy notice*: asks English or Ελληνικά, then shares the parent notice (GDPR Art. 13) as text.
 * [notice] returns null while the tutor's name is blank; then [onNameMissing] runs instead, so the user
 * can add it first. [onError] reports that no app can receive text.
 */
@Composable
fun SharePrivacyNoticeButton(
    notice: (NoticeLanguage) -> String?,
    onNameMissing: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var choosing by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { if (notice(NoticeLanguage.EN) == null) onNameMissing() else choosing = true },
        modifier = modifier,
    ) { Text("Share privacy notice") }

    if (choosing) {
        val share = { language: NoticeLanguage ->
            choosing = false
            val text = notice(language)
            when {
                text == null -> onNameMissing()
                !shareText(context, NOTICE_TITLES.getValue(language), text) -> onError("No app can share text")
            }
        }
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Share privacy notice") },
            text = { Text("Which language should the notice be in?") },
            confirmButton = { TextButton(onClick = { share(NoticeLanguage.EN) }) { Text("English") } },
            dismissButton = { TextButton(onClick = { share(NoticeLanguage.EL) }) { Text("Ελληνικά") } },
        )
    }
}

private val NOTICE_TITLES = mapOf(
    NoticeLanguage.EN to "Privacy notice",
    NoticeLanguage.EL to "Ενημέρωση για την προστασία δεδομένων",
)
