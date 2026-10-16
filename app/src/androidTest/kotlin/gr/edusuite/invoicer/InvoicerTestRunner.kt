package gr.edusuite.invoicer

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Starts the app under test with [TestCourseInvoicerApplication], so instrumented tests never read or write the
 * tutor's real database or settings, even when run on a phone that has the app installed.
 */
class InvoicerTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestCourseInvoicerApplication::class.java.name, context)
}

/** The real app wired to an in-memory database and a separate, emptied settings file. */
class TestCourseInvoicerApplication : CourseInvoicerApplication() {
    override fun createContainer(): AppContainer {
        deleteSharedPreferences(SETTINGS_FILE)
        return AppContainer(this, databaseName = null, settingsFile = SETTINGS_FILE)
    }

    private companion object {
        const val SETTINGS_FILE = "settings-instrumented-tests"
    }
}
