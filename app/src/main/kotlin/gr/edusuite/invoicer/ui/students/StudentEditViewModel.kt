package gr.edusuite.invoicer.ui.students

import androidx.lifecycle.SavedStateHandle
import gr.edusuite.invoicer.ui.common.FormDraft
import androidx.lifecycle.ViewModel
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Rate
import gr.edusuite.invoicer.core.model.RateType
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.repository.StudentRepository
import gr.edusuite.invoicer.core.usecase.SaveStudent
import gr.edusuite.invoicer.ui.common.launchCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

data class StudentForm(
    val firstName: String = "",
    val lastName: String = "",
    val className: String = "",
    val guardianName: String = "",
    val phone: String = "",
    val email: String = "",
    val rate: String = "",
    val rateType: RateType = RateType.HOURLY,
    val notes: String = "",
)

data class StudentEditUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val dirty: Boolean = false,
    val isNew: Boolean = true,
    val form: StudentForm = StudentForm(),
    val classSuggestions: List<String> = emptyList(),
    val firstNameError: Boolean = false,
    val rateError: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val savedId: Long? = null,
)

class StudentEditViewModel(
    private val studentId: Long,
    private val students: StudentRepository,
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val draft = FormDraft(savedState)
    private val saveStudent = SaveStudent(students)
    private var original: Student? = null
    private val mutableState = MutableStateFlow(StudentEditUiState(isNew = studentId == 0L))
    val state: StateFlow<StudentEditUiState> = mutableState.asStateFlow()

    init {
        launchCatching(::fail) {
            val all = students.observeStudents().first()
            original = all.firstOrNull { it.id == studentId }
            if (studentId != 0L && original == null) {
                mutableState.update { it.copy(loading = false, notFound = true, message = "This student no longer exists") }
                return@launchCatching
            }
            mutableState.update { current ->
                current.copy(
                    loading = false,
                    form = if (draft.exists) restoreForm() else original?.toForm() ?: current.form,
                    dirty = draft.exists,
                    classSuggestions = all.map { it.className }.filter { it.isNotBlank() }.distinct().sorted(),
                )
            }
        }
    }

    fun update(transform: (StudentForm) -> StudentForm) = edit(transform = transform)

    private fun edit(allowedWhenLocked: Boolean = false, transform: (StudentForm) -> StudentForm) {
        val current = state.value
        if (current.loading || current.notFound || current.saving) return
        val updated = current.copy(form = transform(current.form), dirty = true, firstNameError = false, rateError = false)
        mutableState.value = updated
        rememberDraft(updated.form)
    }

    private fun rememberDraft(form: StudentForm) = draft.save(
        "firstName" to form.firstName,
        "lastName" to form.lastName,
        "className" to form.className,
        "guardianName" to form.guardianName,
        "phone" to form.phone,
        "email" to form.email,
        "rate" to form.rate,
        "rateType" to form.rateType.name,
        "notes" to form.notes,
    )

    private fun restoreForm() = StudentForm(
        firstName = draft.text("firstName"),
        lastName = draft.text("lastName"),
        className = draft.text("className"),
        guardianName = draft.text("guardianName"),
        phone = draft.text("phone"),
        email = draft.text("email"),
        rate = draft.text("rate"),
        rateType = RateType.valueOf(draft.text("rateType", RateType.HOURLY.name)),
        notes = draft.text("notes"),
    )

    fun save() {
        if (state.value.loading || state.value.notFound || state.value.saving) return
        val form = state.value.form
        val rate = Money.parse(form.rate)
        if (form.firstName.isBlank() || rate == null) {
            mutableState.update { it.copy(firstNameError = form.firstName.isBlank(), rateError = rate == null) }
            return
        }
        mutableState.update { it.copy(saving = true) }
        launchCatching({ error -> mutableState.update { it.copy(saving = false, message = error) } }) {
            val base = original ?: Student(firstName = "", rate = Rate(Money.ZERO, RateType.HOURLY))
            val id = saveStudent(
                base.copy(
                    firstName = form.firstName,
                    lastName = form.lastName,
                    className = form.className,
                    guardianName = form.guardianName,
                    phone = form.phone,
                    email = form.email,
                    rate = Rate(rate, form.rateType),
                    notes = form.notes,
                ),
            )
            draft.clear()
            mutableState.update { it.copy(saving = false, dirty = false, savedId = id) }
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun Student.toForm() = StudentForm(
        firstName = firstName,
        lastName = lastName,
        className = className,
        guardianName = guardianName,
        phone = phone,
        email = email,
        rate = rate.amount.toPlainString(),
        rateType = rate.type,
        notes = notes,
    )
    private fun fail(message: String) = mutableState.update { it.copy(loading = false, notFound = it.notFound || it.loading, saving = false, message = message) }

}
