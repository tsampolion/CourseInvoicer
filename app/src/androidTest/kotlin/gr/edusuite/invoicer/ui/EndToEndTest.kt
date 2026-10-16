package gr.edusuite.invoicer.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import gr.edusuite.invoicer.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real app (real database) through the core tutor workflow:
 * add a student → log a lesson → issue an invoice → void it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EndToEndTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Database writes finish on a background thread, so wait for the UI to show their result. */
    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeoutMillis = 10_000)
    }

    /**
     * Opens a section from its Home button. On a small screen the lower buttons start below the fold and the
     * "Log lesson" button can sit over them, so this scrolls to the button and clicks it through its semantics.
     */
    private fun openFromHome(title: String) {
        compose.onNodeWithText(title).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun studentLessonInvoiceAndVoid() {
        val name = "E2E${System.currentTimeMillis() % 1_000_000}"

        // Home is the launcher: each section opens from its button.
        compose.onNodeWithText("Welcome to Course Invoicer").assertExists()
        openFromHome("Students")
        compose.onNodeWithContentDescription("Add student").performClick()
        compose.onNodeWithText("First name *").performTextInput(name)
        compose.onNodeWithText("Hourly rate *").performTextInput("20")
        compose.onNodeWithText("Save").performClick()

        // Saving a new student opens their detail screen.
        waitFor("Record payment")
        compose.onNodeWithText(name).assertExists()

        compose.onNodeWithContentDescription("Log lesson").performClick()
        waitFor("20.00")
        compose.onNodeWithText("Save").performClick()
        waitFor("Lessons (1)")

        compose.onNodeWithText("Invoice").performClick()
        waitFor("1 lesson · Total", substring = true)
        compose.onNodeWithText("Create").performClick()
        waitFor("created", substring = true)
        compose.onNodeWithText("Done").performClick()
        waitFor("Record payment")

        // Back to Students, back to Home, then void the invoice from Statements; it stays listed as void.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("unpaid", substring = true)
        openFromHome("Statements")
        waitFor(name, substring = true)
        compose.onNodeWithContentDescription("Void invoice", substring = true).performClick()
        compose.onNodeWithText("Void").performClick()
        waitFor("voided", substring = true)
    }
}
