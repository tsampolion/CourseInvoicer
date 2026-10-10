package gr.eduinvoice.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The tutor's details, printed at the top of every invoice. */
data class BusinessProfile(
    val name: String = "",
    val address: String = "",
    val taxId: String = "",
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
)
