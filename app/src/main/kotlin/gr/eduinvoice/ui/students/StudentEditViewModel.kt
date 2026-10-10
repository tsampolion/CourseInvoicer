package gr.eduinvoice.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.eduinvoice.core.model.Money
import gr.eduinvoice.core.model.Rate
import gr.eduinvoice.core.model.RateType
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.repository.StudentRepository
import gr.eduinvoice.core.usecase.SaveStudent
import gr.eduinvoice.ui.common.launchCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ViewModel() {
    private val saveStudent = SaveStudent(students)
    private var original: Student? = null
    private val mutableState = MutableStateFlow(StudentEditUiState(isNew = studentId == 0L))
    val state: StateFlow<StudentEditUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val all = students.observeStudents().first()
            original = all.firstOrNull { it.id == studentId }
            mutableState.update { current ->
                current.copy(
                    loading = false,
                    form = original?.toForm() ?: current.form,
                    classSuggestions = all.map { it.className }.filter { it.isNotBlank() }.distinct().sorted(),
                )
            }
        }
    }

    fun update(transform: (StudentForm) -> StudentForm) = mutableState.update {
        it.copy(form = transform(it.form), firstNameError = false, rateError = false)
    }

    fun save() {
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
            mutableState.update { it.copy(saving = false, savedId = id) }
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
}
