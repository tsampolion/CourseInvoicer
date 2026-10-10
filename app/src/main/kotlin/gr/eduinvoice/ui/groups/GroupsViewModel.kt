package gr.eduinvoice.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.eduinvoice.core.model.StudentGroup
import gr.eduinvoice.core.repository.GroupRepository
import gr.eduinvoice.core.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class GroupRow(val group: StudentGroup, val activeMembers: Int)

data class GroupsUiState(
    val loading: Boolean = true,
    val showArchived: Boolean = false,
    val rows: List<GroupRow> = emptyList(),
    val totalCount: Int = 0,
)

class GroupsViewModel(groups: GroupRepository, students: StudentRepository) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    val state: StateFlow<GroupsUiState> =
        combine(groups.observeGroups(), students.observeStudents(), showArchived) { groupList, studentList, archived ->
            val activeIds = studentList.filterNot { it.archived }.map { it.id }.toSet()
            GroupsUiState(
                loading = false,
                showArchived = archived,
                rows = groupList.filter { it.archived == archived }
                    .map { GroupRow(it, it.memberIds.count { id -> id in activeIds }) },
                totalCount = groupList.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    fun onShowArchivedChange(value: Boolean) {
        showArchived.value = value
    }
}
