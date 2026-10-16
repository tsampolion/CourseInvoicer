package gr.edusuite.invoicer

import android.app.Application

open class CourseInvoicerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    /** Instrumented tests override this to run the real app against a throwaway database. */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}
