package gr.edusuite.invoicer.ui.lessons

import androidx.lifecycle.ViewModel
import gr.edusuite.invoicer.core.model.AbsenceCharge
import gr.edusuite.invoicer.core.model.chargeFor
import gr.edusuite.invoicer.core.usecase.MarkPaid
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Rate
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.SettingsRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import gr.edusuite.invoicer.core.usecase.DeleteLesson
import gr.edusuite.invoicer.core.usecase.MAX_LESSON_MINUTES
import gr.edusuite.invoicer.core.usecase.SaveLesson
import gr.edusuite.invoicer.ui.common.launchCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class LessonEditUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val isNew: Boolean = true,
    val students: List<Student> = emptyList(),
    val studentId: Long? = null,
    val groupName: String? = null,
    val date: LocalDate,
    val time: LocalTime,
    val duration: String,
    val fee: String = "",
    /**
     * The fee no longer follows the rate: the user typed it, or a saved lesson's fee differs from what the
     * rate gives for its length (set by hand, or the rate changed since, R2).
     */
    val feeEdited: Boolean = false,
    val absent: Boolean = false,
    /** Whether the absence is charged (a late cancellation); kept while [absent] is off, but not saved. */
    val absenceCharge: AbsenceCharge = AbsenceCharge.NONE,
    val absenceNote: String = "",
    val paid: Boolean = false,
    val notes: String = "",
    /** Lessons on a statement keep their billing fields; see SaveLesson. */
    val locked: Boolean = false,
    val studentError: Boolean = false,
    val durationError: Boolean = false,
    val feeError: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val done: Boolean = false,
) {
    val minutes: Int? get() = duration.toIntOrNull()?.takeIf { it in 1..MAX_LESSON_MINUTES }

    /** Charged as entered (R3): held or a late cancellation, with a fee above zero. Only then can it be paid. */
    val charged: Boolean
        get() = chargeFor(Money.parse(fee) ?: Money.ZERO, absent, absenceCharge).isPositive
}

class LessonEditViewModel(
    private val lessonId: Long,
    initialStudentId: Long,
    students: StudentRepository,
    private val groups: GroupRepository,
    private val lessons: LessonRepository,
    settings: SettingsRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
    private val saveLesson = SaveLesson(lessons)
    private val deleteLesson = DeleteLesson(lessons)
    private var original: Lesson? = null
    private var groupRate: Rate? = null

    private val mutableState = MutableStateFlow(
        LessonEditUiState(
            isNew = lessonId == 0L,
            date = now().toLocalDate(),
            time = LocalTime.of(now().hour, 0),
            duration = settings.settings.value.defaultLessonMinutes.toString(),
        ),
    )
    val state: StateFlow<LessonEditUiState> = mutableState.asStateFlow()

    init {
        launchCatching(::fail) {
            val existing = if (lessonId == 0L) null else lessons.getLesson(lessonId)
            if (lessonId != 0L && existing == null) {
                mutableState.update { it.copy(loading = false, notFound = true, message = "This lesson no longer exists") }
                return@launchCatching
            }
            val group = existing?.groupId?.let { groups.getGroup(it) }
            original = existing
            groupRate = group?.rate
            val all = students.observeStudents().first()
            val pickable = all.filter { !it.archived || it.id == existing?.studentId }
            mutableState.update { current ->
                val loaded = current.copy(loading = false, students = pickable, groupName = group?.name)
                if (existing != null) {
                    val rate = group?.rate ?: all.firstOrNull { it.id == existing.studentId }?.rate
                    loaded.copy(
                        studentId = existing.studentId,
                        date = existing.date,
                        time = existing.startTime,
                        duration = existing.durationMinutes.toString(),
                        fee = existing.fee.toPlainString(),
                        feeEdited = rate?.feeFor(existing.durationMinutes) != existing.fee,
                        absent = existing.absent,
                        absenceCharge = existing.absenceCharge,
                        absenceNote = existing.absenceNote,
                        paid = existing.isPaid && existing.charge.isPositive,
                        notes = existing.notes,
                        locked = existing.isInvoiced,
                    )
                } else {
                    loaded.copy(studentId = initialStudentId.takeIf { id -> pickable.any { it.id == id } })
                        .withCalculatedFee()
                }
            }
        }
    }

    fun selectStudent(id: Long) = edit { it.copy(studentId = id, studentError = false).withCalculatedFee() }

    fun setDate(date: LocalDate) = edit { it.copy(date = date) }

    fun setTime(time: LocalTime) = edit { it.copy(time = time) }

    fun setDuration(text: String) = edit {
        it.copy(duration = text.filter(Char::isDigit).take(3), durationError = false).withCalculatedFee()
    }

    fun setFee(text: String) = edit { it.copy(fee = text, feeEdited = true, feeError = false) }

    /**
     * An excused absence is never charged, so marking a lesson absent clears its payment unless it is a late
     * cancellation (see SaveLesson).
     */
    fun setAbsent(absent: Boolean) = edit {
        it.copy(absent = absent, paid = it.paid && (!absent || it.absenceCharge == AbsenceCharge.LATE_CANCELLATION))
    }

    fun setAbsenceCharge(charge: AbsenceCharge) = edit {
        it.copy(absenceCharge = charge, paid = it.paid && charge == AbsenceCharge.LATE_CANCELLATION)
    }

    /** The reason for an absence stays editable on a statement, like the notes (R7). */
    fun setAbsenceNote(text: String) = edit(allowedWhenLocked = true) { it.copy(absenceNote = text) }

    fun setPaid(paid: Boolean) {
        if (state.value.loading || state.value.notFound || state.value.saving) return
        if (paid) {
            try { MarkPaid.requireCharged(state.value.charged) }
            catch (error: gr.edusuite.invoicer.core.model.DomainException) {
                fail(error.message.orEmpty())
                return
            }
        }
        edit(allowedWhenLocked = true) { it.copy(paid = paid) }
    }

    fun setNotes(text: String) = edit(allowedWhenLocked = true) { it.copy(notes = text) }

    fun save() {
        if (state.value.loading || state.value.notFound || state.value.saving) return
        val current = state.value
        val studentId = current.studentId
        val minutes = current.minutes
        val fee = Money.parse(current.fee)
        if (studentId == null || minutes == null || fee == null) {
            mutableState.update {
                it.copy(studentError = studentId == null, durationError = minutes == null, feeError = fee == null)
            }
            return
        }
        val previous = original
        val lesson = Lesson(
            id = lessonId,
            studentId = studentId,
            groupId = previous?.groupId,
            date = current.date,
            startTime = current.time,
            durationMinutes = minutes,
            fee = fee,
            absent = current.absent,
            notes = current.notes,
            paidOn = if (current.paid && current.charged) previous?.paidOn ?: now().toLocalDate() else null,
            invoiceId = previous?.invoiceId,
            absenceCharge = current.absenceCharge,
            absenceNote = current.absenceNote,
        )
        mutableState.update { it.copy(saving = true) }
        launchCatching(::fail) {
            saveLesson(lesson)
            mutableState.update { it.copy(saving = false, done = true) }
        }
    }

    fun delete() = launchCatching(::fail) {
        deleteLesson(lessonId)
        mutableState.update { it.copy(done = true) }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun fail(message: String) = mutableState.update { it.copy(loading = false, notFound = it.notFound || it.loading, saving = false, message = message) }

    /** Billing fields are read-only while the lesson is on a statement. */
    private fun edit(allowedWhenLocked: Boolean = false, transform: (LessonEditUiState) -> LessonEditUiState) = mutableState.update {
        if (it.loading || it.notFound || it.saving || (it.locked && !allowedWhenLocked)) it else transform(it)
    }

    private fun LessonEditUiState.withCalculatedFee(): LessonEditUiState {
        if (feeEdited) return this
        val rate = groupRate ?: students.firstOrNull { it.id == studentId }?.rate ?: return this
        val minutes = minutes ?: return this
        return copy(fee = rate.feeFor(minutes).toPlainString())
    }
}
