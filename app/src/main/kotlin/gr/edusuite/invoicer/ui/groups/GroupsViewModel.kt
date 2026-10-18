package gr.edusuite.invoicer.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import gr.edusuite.invoicer.ui.common.stateInCatching

data class GroupRow(val group: StudentGroup, val activeMembers: Int)

data class GroupsUiState(
    val loading: Boolean = true,
    val showArchived: Boolean = false,
    val rows: List<GroupRow> = emptyList(),
    val totalCount: Int = 0,
    val message: String? = null,
)

class GroupsViewModel(groups: GroupRepository, students: StudentRepository) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    private fun showMessage(text: String) { message.value = text }
    fun messageShown() { message.value = null }

    private val showArchived = MutableStateFlow(false)

    private val observedState: StateFlow<GroupsUiState> =
        combine(groups.observeGroups(), students.observeStudents(), showArchived) { groupList, studentList, archived ->
            val activeIds = studentList.filterNot { it.archived }.map { it.id }.toSet()
            GroupsUiState(
                loading = false,
                showArchived = archived,
                rows = groupList.filter { it.archived == archived }
                    .map { GroupRow(it, it.memberIds.count { id -> id in activeIds }) },
                totalCount = groupList.size,
            )
        }.stateInCatching(viewModelScope, GroupsUiState(), ::showMessage)

    // Keep error delivery alive even after the repository flow has completed with an error.
    val state: StateFlow<GroupsUiState> = combine(observedState, message) { snapshot, event ->
        snapshot.copy(message = event)
    }.stateInCatching(viewModelScope, GroupsUiState(), ::showMessage)


    fun onShowArchivedChange(value: Boolean) {
        showArchived.value = value
    }
}
