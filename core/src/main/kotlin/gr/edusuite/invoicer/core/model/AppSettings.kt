package gr.edusuite.invoicer.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The tutor's details, printed at the top of every invoice. */
data class BusinessProfile(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val iban: String = "",
)

data class AppSettings(
    val business: BusinessProfile = BusinessProfile(),
    val currencyCode: String = "EUR",
    val defaultLessonMinutes: Int = 60,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val invoiceFooter: String = "",
    val reminders: ReminderSettings = ReminderSettings(),
)

/**
 * The month-end reminder to send statements (R15). [enabled] turns every reminder off; each early switch
 * moves that month's reminder from the 29th to the 21st.
 */
data class ReminderSettings(
    val enabled: Boolean = true,
    val earlyDecember: Boolean = true,
    val earlyJune: Boolean = true,
    val earlyEaster: Boolean = true,
)
