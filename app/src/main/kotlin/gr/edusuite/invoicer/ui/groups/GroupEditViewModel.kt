package gr.edusuite.invoicer.ui.groups

import androidx.lifecycle.SavedStateHandle
import gr.edusuite.invoicer.ui.common.FormDraft
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
    val dirty: Boolean = false,
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
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val draft = FormDraft(savedState)
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
                    dirty = draft.exists,
                    form = if (draft.exists) restoreForm() else group?.let {
                        GroupForm(it.name, it.className, it.rate.amount.toPlainString(), it.rate.type, it.memberIds, it.archived)
                    } ?: current.form,
                )
            }
        }
    }

    fun update(transform: (GroupForm) -> GroupForm) = edit(transform = transform)

    private fun edit(allowedWhenLocked: Boolean = false, transform: (GroupForm) -> GroupForm) {
        val current = state.value
        if (current.loading || current.notFound || current.saving) return
        val updated = current.copy(form = transform(current.form), dirty = true, nameError = false, rateError = false)
        mutableState.value = updated
        rememberDraft(updated.form)
    }

    private fun rememberDraft(form: GroupForm) = draft.save(
        "name" to form.name,
        "className" to form.className,
        "rate" to form.rate,
        "rateType" to form.rateType.name,
        "memberIds" to form.memberIds.toLongArray(),
        "archived" to form.archived,
    )

    private fun restoreForm() = GroupForm(
        name = draft.text("name"),
        className = draft.text("className"),
        rate = draft.text("rate"),
        rateType = RateType.valueOf(draft.text("rateType", RateType.HOURLY.name)),
        memberIds = draft.ids("memberIds"),
        archived = draft.flag("archived"),
    )

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
            draft.clear()
            mutableState.update { it.copy(saving = false, dirty = false, done = true) }
        }
    }

    fun delete() = launchCatching(::fail) {
        deleteGroup(groupId)
        draft.clear()
        mutableState.update { it.copy(done = true, dirty = false) }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun fail(message: String) = mutableState.update { it.copy(loading = false, notFound = it.notFound || it.loading, saving = false, message = message) }
}
