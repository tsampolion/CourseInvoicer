package gr.eduinvoice.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.eduinvoice.core.model.Money
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.report.Reports
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StudentRow(val student: Student, val outstanding: Money)

data class StudentsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val showArchived: Boolean = false,
    val rows: List<StudentRow> = emptyList(),
    val totalCount: Int = 0,
)

class StudentsViewModel(students: StudentRepository, lessons: LessonRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val showArchived = MutableStateFlow(false)

    val state: StateFlow<StudentsUiState> = combine(
        students.observeStudents(),
        lessons.observeLessons(),
        query,
        showArchived,
    ) { studentList, lessonList, text, archived ->
        val balances = Reports.balances(lessonList).associate { it.studentId to it.outstanding }
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
        StudentsUiState(false, text, archived, rows, studentList.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentsUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onShowArchivedChange(value: Boolean) {
        showArchived.value = value
    }
}
