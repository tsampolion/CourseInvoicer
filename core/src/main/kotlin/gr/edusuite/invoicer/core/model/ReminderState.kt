package gr.edusuite.invoicer.core.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * How the tutor answered the month-end reminder (R15). It stays on this device: it is neither a setting
 * nor part of a backup.
 *
 * @property doneFor the latest month marked "Done for this month"; it and earlier months are not reminded.
 * @property snoozedUntil set by "Remind me tomorrow": no reminder before this day.
 */
data class ReminderState(
    val doneFor: YearMonth? = null,
    val snoozedUntil: LocalDate? = null,
)
