package gr.edusuite.invoicer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import gr.edusuite.invoicer.CourseInvoicerApplication
import gr.edusuite.invoicer.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real app (real database) through the core tutor workflow:
 * add a student → log a lesson → issue a statement → void it.
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

    /** Adds a student at 20 an hour; saving opens their detail screen. */
    private fun addStudent(name: String) {
        compose.onNodeWithText("Students").performClick()
        compose.onNodeWithContentDescription("Add student").performClick()
        compose.onNodeWithText("First name *").performTextInput(name)
        compose.onNodeWithText("Hourly rate *").performTextInput("20")
        compose.onNodeWithText("Save").performClick()
        waitFor("Record payment")
        compose.onNodeWithText(name).assertExists()
    }

    @Test
    fun studentLessonStatementAndVoid() {
        val name = "E2E${System.currentTimeMillis() % 1_000_000}"
        addStudent(name)

        compose.onNodeWithContentDescription("Log lesson").performClick()
        waitFor("20.00")
        compose.onNodeWithText("Save").performClick()
        waitFor("Lessons (1)")

        compose.onNodeWithText("Statement").performClick()
        waitFor("1 lesson · Total", substring = true)
        compose.onNodeWithText("Create").performClick()
        waitFor("created", substring = true)
        compose.onNodeWithText("Done").performClick()
        waitFor("Record payment")

        // Back to the Students tab, then void the statement from the Invoices tab; it stays listed as void.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Invoices").performClick()
        waitFor(name, substring = true)
        compose.onNodeWithContentDescription("Void statement", substring = true).performClick()
        compose.onNodeWithText("Void").performClick()
        waitFor("voided", substring = true)
    }

    @Test
    fun anExcusedAbsenceGoesOnAStatementAtZero() {
        val name = "Absent${System.currentTimeMillis() % 1_000_000}"
        addStudent(name)

        compose.onNodeWithContentDescription("Log lesson").performClick()
        waitFor("20.00")
        compose.onNodeWithText("Absent").performScrollTo().performClick()
        waitFor("Not charged")
        compose.onNodeWithText("Reason (optional)").performScrollTo().performTextInput("ill")
        compose.onNodeWithText("Save").performClick()
        waitFor("Lessons (1)")

        compose.onNodeWithText("Statement").performClick()
        waitFor("1 lesson · Total", substring = true)
        compose.onNodeWithText("Absent, not charged", substring = true).assertExists()
        compose.onNodeWithText("1 lesson · Total", substring = true).assertTextContains("0.00", substring = true)
        compose.onNodeWithText("Create").performClick()
        waitFor("created", substring = true)
        compose.onNodeWithText("Done").performClick()
        waitFor("Record payment")
    }

    @Test
    fun settingsSaveAsYouTypeWithoutASaveButton() {
        val name = "Tutor${System.currentTimeMillis() % 1_000_000}"
        val settings = (compose.activity.application as CourseInvoicerApplication).container.settingsRepository

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Name or business name").performTextInput(name)
        compose.onAllNodesWithText("Save").assertCountEquals(0)

        // Leaving the screen saves what was typed without waiting for the pause.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { settings.settings.value.business.name == name }

        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor(name)
    }
}
