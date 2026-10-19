package gr.edusuite.invoicer.ui.invoices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.edusuite.invoicer.ui.common.CommitField
import gr.edusuite.invoicer.ui.common.FormSpacing
import gr.edusuite.invoicer.ui.common.MessageEffect
import gr.edusuite.invoicer.ui.common.ScreenScaffold
import gr.edusuite.invoicer.ui.common.SharePrivacyNoticeButton
import gr.edusuite.invoicer.ui.common.appViewModel
import gr.edusuite.invoicer.ui.lessons.SwitchRow

/**
 * The tutor's details printed on every statement (R13, R14): name, address, phone, email, IBAN and footer.
 * Opens from the top of Statements. Saves as you type, like Settings.
 */
@Composable
fun StatementDetailsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatementDetailsViewModel = appViewModel { StatementDetailsViewModel(it.settingsRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val form = state.form
    MessageEffect(state.message, snackbar, viewModel::messageShown)

    // Save what is still waiting before the screen goes away. System Back clears this screen's ViewModel
    // at once, so it is handled here rather than by the NavHost.
    val leave = {
        viewModel.flush()
        onBack()
    }
    BackHandler(onBack = leave)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    ScreenScaffold(
        title = "Statement details",
        modifier = modifier,
        onBack = leave,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = FormSpacing,
        ) {
            Text(
                "Printed at the top of every new statement. Statements already issued keep the details they had.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "You are responsible for the personal data you keep here (GDPR). " +
                    "Share a privacy notice with your students.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SharePrivacyNoticeButton(
                notice = viewModel::privacyNotice,
                onNameMissing = { viewModel.showMessage("Add your name first") },
                onError = viewModel::showMessage,
            )
            CommitField(
                value = form.name,
                onValueChange = viewModel::onName,
                label = "Name or business name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            CommitField(
                value = form.address,
                onValueChange = viewModel::onAddress,
                label = "Address",
                singleLine = false,
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            SwitchRow(
                "Show address on statements",
                form.showAddressOnStatements,
                viewModel::onShowAddressOnStatements,
            )
            CommitField(
                value = form.phone,
                onValueChange = viewModel::onPhone,
                label = "Phone",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            CommitField(
                value = form.email,
                onValueChange = viewModel::onEmail,
                label = "Email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            CommitField(
                value = form.iban,
                onValueChange = viewModel::onIban,
                label = "IBAN for payments",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            CommitField(
                value = form.footer,
                onValueChange = viewModel::onFooter,
                label = "Statement footer (e.g. thank-you note)",
                singleLine = false,
                minLines = 2,
            )
        }
    }
}
