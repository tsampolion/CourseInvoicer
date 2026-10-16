package gr.eduinvoice

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Starts the app under test with [TestEduInvoiceApplication], so instrumented tests never read or write the
 * tutor's real database or settings, even when run on a phone that has the app installed.
 */
class EduTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestEduInvoiceApplication::class.java.name, context)
}

/** The real app wired to an in-memory database and a separate, emptied settings file. */
class TestEduInvoiceApplication : EduInvoiceApplication() {
    override fun createContainer(): AppContainer {
        deleteSharedPreferences(SETTINGS_FILE)
        return AppContainer(this, databaseName = null, settingsFile = SETTINGS_FILE)
    }

    private companion object {
        const val SETTINGS_FILE = "settings-instrumented-tests"
    }
}
