package gr.edusuite.invoicer

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import kotlinx.coroutines.runBlocking

/**
 * Starts the app under test with [TestCourseInvoicerApplication], so instrumented tests never read or write the
 * tutor's real database or settings, even when run on a phone that has the app installed.
 */
class InvoicerTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestCourseInvoicerApplication::class.java.name, context)
}

/** The real app wired to an in-memory database and separate, emptied settings and reminder files. */
class TestCourseInvoicerApplication : CourseInvoicerApplication() {
    override fun createContainer(): AppContainer {
        deleteSharedPreferences(SETTINGS_FILE)
        deleteSharedPreferences(REMINDER_STATE_FILE)
        val container = AppContainer(
            this,
            databaseName = null,
            settingsFile = SETTINGS_FILE,
            reminderStateFile = REMINDER_STATE_FILE,
        )
        // The month-end reminder opens a dialog over Home on some dates; tests start with it off so they
        // behave the same on every day of the year.
        runBlocking {
            container.settingsRepository.update { it.copy(reminders = it.reminders.copy(enabled = false)) }
        }
        return container
    }

    private companion object {
        const val SETTINGS_FILE = "settings-instrumented-tests"
        const val REMINDER_STATE_FILE = "reminder-state-instrumented-tests"
    }
}
