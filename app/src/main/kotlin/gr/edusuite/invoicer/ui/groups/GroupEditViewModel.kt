package gr.edusuite.invoicer.ui.groups

import androidx.lifecycle.ViewModel
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Rate
import gr.edusuite.invoicer.core.model.RateType
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.repository.GroupRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import gr.edusuite.invoicer.core.usecase.DeleteGroup
import gr.edusuite.invoicer.core.usecase.SaveGroup
import gr.edusuite.invoicer.ui.common.launchCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

data class GroupForm(
    val name: String = "",
    val className: String = "",
    val rate: String = "",
    val rateType: RateType = RateType.HOURLY,
    val memberIds: Set<Long> = emptySet(),
    val archived: Boolean = false,
)

data class GroupEditUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val isNew: Boolean = true,
    val form: GroupForm = GroupForm(),
    /** Active students plus any archived student who is still a member. */
    val candidates: List<Student> = emptyList(),
    val nameError: Boolean = false,
    val rateError: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val done: Boolean = false,
)

class GroupEditViewModel(
    private val groupId: Long,
    private val groups: GroupRepository,
    private val students: StudentRepository,
    lessons: LessonRepository,
) : ViewModel() {
    private val saveGroup = SaveGroup(groups)
    private val deleteGroup = DeleteGroup(groups, lessons)
    private val mutableState = MutableStateFlow(GroupEditUiState(isNew = groupId == 0L))
    val state: StateFlow<GroupEditUiState> = mutableState.asStateFlow()

    init {
        launchCatching(::fail) {
            val group = if (groupId == 0L) null else groups.getGroup(groupId)
            if (groupId != 0L && group == null) {
                mutableState.update { it.copy(loading = false, notFound = true, message = "This group no longer exists") }
                return@launchCatching
            }
            val members = group?.memberIds.orEmpty()
            val candidates = students.observeStudents().first().filter { !it.archived || it.id in members }
            mutableState.update { current ->
                current.copy(
                    loading = false,
                    candidates = candidates,
                    form = group?.let {
                        GroupForm(it.name, it.className, it.rate.amount.toPlainString(), it.rate.type, it.memberIds, it.archived)
                    } ?: current.form,
                )
            }
        }
    }

    fun update(transform: (GroupForm) -> GroupForm) = edit(transform = transform)

    private fun edit(allowedWhenLocked: Boolean = false, transform: (GroupForm) -> GroupForm) = mutableState.update {
        if (it.loading || it.notFound || it.saving) it
        else it.copy(form = transform(it.form), nameError = false, rateError = false)
    }

    fun toggleMember(studentId: Long) = update {
        it.copy(memberIds = if (studentId in it.memberIds) it.memberIds - studentId else it.memberIds + studentId)
    }

    fun save() {
        if (state.value.loading || state.value.notFound || state.value.saving) return
        val form = state.value.form
        val rate = Money.parse(form.rate)
        if (form.name.isBlank() || rate == null) {
            mutableState.update { it.copy(nameError = form.name.isBlank(), rateError = rate == null) }
            return
        }
        mutableState.update { it.copy(saving = true) }
        launchCatching(::fail) {
            saveGroup(
                StudentGroup(
                    id = groupId,
                    name = form.name,
                    className = form.className,
                    rate = Rate(rate, form.rateType),
                    memberIds = form.memberIds,
                    archived = form.archived,
                ),
            )
            mutableState.update { it.copy(saving = false, done = true) }
        }
    }

    fun delete() = launchCatching(::fail) {
        deleteGroup(groupId)
        mutableState.update { it.copy(done = true) }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun fail(message: String) = mutableState.update { it.copy(loading = false, notFound = it.notFound || it.loading, saving = false, message = message) }
}
