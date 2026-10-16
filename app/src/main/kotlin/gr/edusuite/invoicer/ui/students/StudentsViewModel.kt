package gr.edusuite.invoicer.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StudentRow(val student: Student, val outstanding: Money)

data class StudentsUiState(
    val loading: Boolean = true,
    val showArchived: Boolean = false,
    val rows: List<StudentRow> = emptyList(),
    val totalCount: Int = 0,
)

/** The search text itself lives in the screen's text field state; this view model only filters by it. */
class StudentsViewModel(students: StudentRepository, lessons: LessonRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val showArchived = MutableStateFlow(false)

    val state: StateFlow<StudentsUiState> = combine(
        students.observeStudents(),
        lessons.observeBalances(),
        query,
        showArchived,
    ) { studentList, balanceList, text, archived ->
        val balances = balanceList.associate { it.studentId to it.outstanding }
        val terms = text.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        val rows = studentList
            .filter { it.archived == archived }
            .filter { student ->
                val haystack = listOf(student.fullName, student.className, student.guardianName, student.phone)
                    .joinToString(" ")
                    .lowercase()
                terms.all { it in haystack }
            }
            .map { StudentRow(it, balances[it.id] ?: Money.ZERO) }
        StudentsUiState(false, archived, rows, studentList.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentsUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onShowArchivedChange(value: Boolean) {
        showArchived.value = value
    }
}
