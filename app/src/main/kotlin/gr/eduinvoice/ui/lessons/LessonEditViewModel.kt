package gr.eduinvoice.ui.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.Money
import gr.eduinvoice.core.model.Rate
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.repository.GroupRepository
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.SettingsRepository
import gr.eduinvoice.core.repository.StudentRepository
import gr.eduinvoice.core.usecase.DeleteLesson
import gr.eduinvoice.core.usecase.MAX_LESSON_MINUTES
import gr.eduinvoice.core.usecase.SaveLesson
import gr.eduinvoice.ui.common.launchCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class LessonEditUiState(
    val loading: Boolean = true,
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
    val paid: Boolean = false,
    val notes: String = "",
    /** Invoiced lessons keep their billing fields; see SaveLesson. */
    val locked: Boolean = false,
    val studentError: Boolean = false,
    val durationError: Boolean = false,
    val feeError: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val done: Boolean = false,
) {
    val minutes: Int? get() = duration.toIntOrNull()?.takeIf { it in 1..MAX_LESSON_MINUTES }
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
        viewModelScope.launch {
            val existing = if (lessonId == 0L) null else lessons.getLesson(lessonId)
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
                        paid = existing.isPaid && !existing.absent,
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

    /** Absences are never charged, so marking one absent also clears its payment (see SaveLesson). */
    fun setAbsent(absent: Boolean) = edit { it.copy(absent = absent, paid = it.paid && !absent) }

    fun setPaid(paid: Boolean) = mutableState.update { it.copy(paid = paid) }

    fun setNotes(text: String) = mutableState.update { it.copy(notes = text) }

    fun save() {
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
            paidOn = if (current.paid) previous?.paidOn ?: now().toLocalDate() else null,
            invoiceId = previous?.invoiceId,
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

    private fun fail(message: String) = mutableState.update { it.copy(saving = false, message = message) }

    /** Billing fields are read-only while the lesson is on an invoice. */
    private fun edit(transform: (LessonEditUiState) -> LessonEditUiState) = mutableState.update {
        if (it.locked) it else transform(it)
    }

    private fun LessonEditUiState.withCalculatedFee(): LessonEditUiState {
        if (feeEdited) return this
        val rate = groupRate ?: students.firstOrNull { it.id == studentId }?.rate ?: return this
        val minutes = minutes ?: return this
        return copy(fee = rate.feeFor(minutes).toPlainString())
    }
}
