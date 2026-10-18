package gr.edusuite.invoicer.ui

import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import gr.edusuite.invoicer.ui.students.StudentEditViewModel
import gr.edusuite.invoicer.core.model.AbsenceCharge
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Rate
import gr.edusuite.invoicer.core.model.RateType
import gr.edusuite.invoicer.core.model.ReminderSettings
import gr.edusuite.invoicer.core.model.ReminderState
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.model.ThemeMode
import gr.edusuite.invoicer.core.privacy.NoticeLanguage
import gr.edusuite.invoicer.core.testing.InMemoryReminderStateRepository
import gr.edusuite.invoicer.core.testing.InMemoryRepositories
import gr.edusuite.invoicer.core.usecase.CreateInvoice
import gr.edusuite.invoicer.core.usecase.InvoiceRenderer
import gr.edusuite.invoicer.data.BackupFiles
import gr.edusuite.invoicer.ui.dashboard.DashboardViewModel
import gr.edusuite.invoicer.ui.groups.GroupEditViewModel
import gr.edusuite.invoicer.ui.groups.GroupLessonViewModel
import gr.edusuite.invoicer.ui.groups.Presence
import gr.edusuite.invoicer.ui.home.HomeViewModel
import gr.edusuite.invoicer.ui.home.ReminderPrompt
import gr.edusuite.invoicer.ui.invoices.CreateInvoiceViewModel
import gr.edusuite.invoicer.ui.invoices.InvoicesViewModel
import gr.edusuite.invoicer.ui.invoices.StatementDetailsViewModel
import gr.edusuite.invoicer.ui.lessons.LessonEditViewModel
import gr.edusuite.invoicer.ui.lessons.LessonsViewModel
import gr.edusuite.invoicer.ui.settings.SAVE_DEBOUNCE_MS
import gr.edusuite.invoicer.ui.settings.SettingsViewModel
import gr.edusuite.invoicer.ui.students.StudentsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repos = InMemoryRepositories()
    private val createInvoice = CreateInvoice(repos, repos, InvoiceRenderer(repos, repos, repos.settingsRepository))

    private suspend fun addStudent(name: String, rate: Rate = Rate(Money(2000), RateType.HOURLY)) =
        repos.saveStudent(Student(firstName = name, rate = rate))

    private fun lesson(studentId: Long, date: String, fee: Long, paidOn: String? = null) = Lesson(
        studentId = studentId,
        date = LocalDate.parse(date),
        startTime = LocalTime.of(17, 0),
        durationMinutes = 60,
        fee = Money(fee),
        paidOn = paidOn?.let(LocalDate::parse),
    )

    @Test fun `lesson deletion posts app Undo which restores after the editor is done`() = runTest {
        val student = addStudent("Maria")
        val id = repos.saveLesson(lesson(student, "2026-03-10", 2000))
        val before = repos.getLesson(id)
        val messages = gr.edusuite.invoicer.ui.common.Messages()
        val posted = mutableListOf<gr.edusuite.invoicer.ui.common.UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { messages.flow.collect { posted += it } }
        val vm = LessonEditViewModel(id, student, repos, repos, repos, repos.settingsRepository, messages = messages)
        vm.delete()
        assertTrue(vm.state.value.done)
        assertNull(repos.getLesson(id))
        assertEquals("Lesson deleted", posted.single().text)
        assertEquals("Undo", posted.single().actionLabel)
        posted.single().action!!.invoke()
        assertEquals(before, repos.getLesson(id))
    }

    @Test fun `erasing a student's data offers Undo and export holds their data`() = runTest {
        val student = addStudent("Maria")
        repos.saveLesson(lesson(student, "2026-03-10", 2000).copy(notes = "Unit 4"))
        val messages = gr.edusuite.invoicer.ui.common.Messages()
        val posted = mutableListOf<gr.edusuite.invoicer.ui.common.UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { messages.flow.collect { posted += it } }
        val vm = gr.edusuite.invoicer.ui.students.StudentDetailViewModel(
            student, repos, repos, repos, repos, repos.settingsRepository, messages = messages,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        val (name, json) = vm.exportData()!!
        assertEquals("Maria", name)
        assertTrue(json.contains("\"course-invoicer-student-data\"") && json.contains("Unit 4"))

        vm.eraseData()
        assertEquals("Erased student", repos.getStudent(student)!!.fullName)
        assertEquals("Undo", posted.single().actionLabel)
        posted.single().action!!.invoke()
        assertEquals("Maria", repos.getStudent(student)!!.fullName)
        assertEquals("Unit 4", repos.lessons.single().notes)
    }

    @Test fun `archived students long past their last lesson and paid up are due for erasure`() = runTest {
        val paidUp = addStudent("Old")
        val owes = addStudent("Owes")
        val recent = addStudent("Recent")
        repos.saveLesson(lesson(paidUp, "2025-01-10", 2000, paidOn = "2025-01-10"))
        repos.saveLesson(lesson(owes, "2025-01-10", 2000))
        repos.saveLesson(lesson(recent, "2026-01-10", 2000, paidOn = "2026-01-10"))
        listOf(paidUp, owes, recent).forEach { id -> repos.saveStudent(repos.getStudent(id)!!.copy(archived = true)) }
        val viewModel = StudentsViewModel(repos, repos, repos) { LocalDate.parse("2026-03-15") }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.onShowArchivedChange(true)

        val due = viewModel.state.value.rows.associate { it.student.firstName to it.dueForErasure }
        assertEquals(mapOf("Old" to true, "Owes" to false, "Recent" to false), due)
    }

    @Test fun `record payment acts immediately and offers Undo`() = runTest {
        val student = addStudent("Maria")
        val id = repos.saveLesson(lesson(student, "2026-03-10", 2000))
        val messages = gr.edusuite.invoicer.ui.common.Messages()
        val posted = mutableListOf<gr.edusuite.invoicer.ui.common.UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { messages.flow.collect { posted += it } }
        val vm = gr.edusuite.invoicer.ui.students.StudentDetailViewModel(
            student, repos, repos, repos, repos, repos.settingsRepository,
            today = { LocalDate.parse("2026-03-20") }, messages = messages,
        )
        vm.recordPayment()
        assertEquals(LocalDate.parse("2026-03-20"), repos.getLesson(id)?.paidOn)
        assertEquals("Undo", posted.single().actionLabel)
        posted.single().action!!.invoke()
        assertNull(repos.getLesson(id)?.paidOn)
    }

    @Test fun `group archive is immediate without saving other form edits and Undo keeps those edits`() = runTest {
        val group = repos.saveGroup(StudentGroup(name = "Class", rate = Rate(Money(2000), RateType.HOURLY)))
        val messages = gr.edusuite.invoicer.ui.common.Messages()
        val posted = mutableListOf<gr.edusuite.invoicer.ui.common.UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { messages.flow.collect { posted += it } }
        val vm = GroupEditViewModel(group, repos, repos, repos, messages = messages)
        vm.update { it.copy(name = "Draft name") }
        vm.setArchived(true)
        assertTrue(repos.getGroup(group)!!.archived)
        assertEquals("Class", repos.getGroup(group)!!.name)
        posted.single().action!!.invoke()
        assertFalse(repos.getGroup(group)!!.archived)
        assertFalse(vm.state.value.form.archived)
        assertEquals("Draft name", vm.state.value.form.name)
        vm.save()
        assertFalse(repos.getGroup(group)!!.archived)
        assertEquals("Draft name", repos.getGroup(group)!!.name)
    }

    @Test fun `lesson draft restores typed fields and clears only after a successful save`() = runTest {
        val maria = addStudent("Maria")
        val saved = SavedStateHandle(mapOf(
            "draft.present" to true, "draft.studentId" to maria,
            "draft.date" to LocalDate.of(2026, 3, 11).toEpochDay(), "draft.time" to "16:30",
            "draft.duration" to "90", "draft.fee" to "31.25", "draft.feeEdited" to true,
            "draft.notes" to "typed homework", "draft.locked" to false,
        ))
        val vm = LessonEditViewModel(0, 0, repos, repos, repos, repos.settingsRepository, savedState = saved)
        assertEquals("typed homework", vm.state.value.notes)
        assertEquals("31.25", vm.state.value.fee)
        assertEquals(LocalTime.of(16, 30), vm.state.value.time)
        assertTrue(vm.state.value.dirty)
        vm.setDuration("0")
        vm.save()
        assertTrue(saved.keys().any { it.startsWith("draft.") })
        vm.setDuration("90")
        vm.save()
        assertFalse(vm.state.value.dirty)
        assertFalse(saved.keys().any { it.startsWith("draft.") })
        assertEquals("typed homework", repos.lessons.single().notes)
    }

    @Test fun `restored lesson draft cannot bypass a new statement lock`() = runTest {
        val maria = addStudent("Maria")
        val id = repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        val saved = SavedStateHandle()
        val original = LessonEditViewModel(id, 0, repos, repos, repos, repos.settingsRepository, savedState = saved)
        original.setFee("99")
        createInvoice(maria, listOf(id), LocalDate.of(2026, 3, 31))
        val restored = LessonEditViewModel(id, 0, repos, repos, repos, repos.settingsRepository, savedState = saved)
        assertTrue(restored.state.value.locked)
        assertEquals("20.00", restored.state.value.fee)
        assertFalse(restored.state.value.dirty)
        assertTrue(restored.state.value.message!!.contains("statement status changed"))
        assertFalse(saved.keys().any { it.startsWith("draft.") })
    }

    @Test fun `student and group drafts restore and clear on save`() = runTest {
        val studentDraft = SavedStateHandle()
        val first = StudentEditViewModel(0, repos, studentDraft)
        first.update { it.copy(firstName = "Draft student", rate = "25", notes = "typed") }
        val restored = StudentEditViewModel(0, repos, studentDraft)
        assertTrue(restored.state.value.dirty)
        assertEquals("typed", restored.state.value.form.notes)
        restored.save()
        assertFalse(restored.state.value.dirty)
        assertTrue(studentDraft.keys().isEmpty())
        val groupDraft = SavedStateHandle()
        val group = GroupEditViewModel(0, repos, repos, repos, groupDraft)
        group.update { it.copy(name = "Draft group", rate = "12", memberIds = setOf(repos.students.single().id)) }
        val restoredGroup = GroupEditViewModel(0, repos, repos, repos, groupDraft)
        assertTrue(restoredGroup.state.value.dirty)
        assertEquals("Draft group", restoredGroup.state.value.form.name)
        restoredGroup.save()
        assertFalse(restoredGroup.state.value.dirty)
        assertTrue(groupDraft.keys().isEmpty())
    }

    @Test fun `statement draft restores selection and clears after issue`() = runTest {
        val maria = addStudent("Maria")
        val firstId = repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        val secondId = repos.saveLesson(lesson(maria, "2026-03-11", 2000))
        val saved = SavedStateHandle()
        repos.settingsRepository.update { it.copy(business = it.business.copy(name = "Tutor")) }
        fun form() = CreateInvoiceViewModel(maria, repos, repos, repos, repos,
            InvoiceRenderer(repos, repos, repos.settingsRepository), repos.settingsRepository, savedState = saved)
        val original = form()
        assertFalse(original.state.value.dirty)
        original.toggle(secondId)
        original.setNotes("draft statement")
        val restored = form()
        assertEquals(setOf(firstId), restored.state.value.selected)
        assertEquals("draft statement", restored.state.value.notes)
        assertTrue(restored.state.value.dirty)
        restored.create()
        assertFalse(restored.state.value.dirty)
        assertTrue(saved.keys().isEmpty())
    }

    @Test fun `home exposes database damage and acknowledging calls through`() = runTest {
        var acknowledged = false
        val damage = kotlinx.coroutines.flow.MutableStateFlow(true)
        val vm = HomeViewModel(repos, repos, repos, repos, repos.settingsRepository,
            InMemoryReminderStateRepository(), databaseDamaged = damage,
            acknowledgeDamage = { acknowledged = true; damage.value = false })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertTrue(vm.state.value.databaseDamaged)
        vm.acknowledgeDamagedDatabase().join()
        runCurrent()
        assertTrue(acknowledged)
        assertFalse(vm.state.value.databaseDamaged)
    }

    @Test fun `a failed list flow reports an error and can clear it without losing the last data`() = runTest {
        val maria = addStudent("Maria")
        val failure = kotlinx.coroutines.CompletableDeferred<Unit>()
        val broken = object : gr.edusuite.invoicer.core.repository.StudentRepository by repos {
            override fun observeStudents() = kotlinx.coroutines.flow.flow {
                emit(listOf(repos.getStudent(maria)!!))
                failure.await()
                throw IllegalStateException("disk")
            }
        }
        val vm = StudentsViewModel(broken, repos, repos)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(1, vm.state.value.rows.size)
        failure.complete(Unit)
        assertEquals("Something went wrong: disk", vm.state.value.message)
        assertEquals("Maria", vm.state.value.rows.single().student.firstName)
        vm.messageShown()
        assertNull(vm.state.value.message)
        assertEquals(1, vm.state.value.rows.size)
    }

    @Test fun `missing lesson is not an editable blank form`() = runTest {
        val vm = LessonEditViewModel(999, 0, repos, repos, repos, repos.settingsRepository)
        assertTrue(vm.state.value.notFound)
        assertEquals("This lesson no longer exists", vm.state.value.message)
        vm.setNotes("ignored")
        vm.save()
        assertEquals("", vm.state.value.notes)
        assertTrue(repos.lessons.isEmpty())
    }

    @Test fun `an excused lesson form refuses payment and reports the core rule`() = runTest {
        val maria = addStudent("Maria")
        val id = repos.saveLesson(lesson(maria, "2026-03-10", 2000).copy(absent = true))
        val vm = LessonEditViewModel(id, 0, repos, repos, repos, repos.settingsRepository)
        vm.setPaid(true)
        assertFalse(vm.state.value.paid)
        assertEquals("An excused absence is not charged, so it can't be paid", vm.state.value.message)
        assertNull(repos.getLesson(id)?.paidOn)
    }

    @Test fun `bulk payment skips selected excused absences`() = runTest {
        val maria = addStudent("Maria")
        val held = repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        val excused = repos.saveLesson(lesson(maria, "2026-03-11", 2000).copy(absent = true))
        val day = LocalDate.of(2026, 3, 31)
        val messages = gr.edusuite.invoicer.ui.common.Messages()
        val posted = mutableListOf<gr.edusuite.invoicer.ui.common.UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { messages.flow.collect { posted += it } }
        val vm = LessonsViewModel(repos, repos, repos, today = { day }, messages = messages)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.toggleSelection(held)
        vm.toggleSelection(excused)
        vm.markSelectedPaid()
        assertEquals(day, repos.getLesson(held)?.paidOn)
        assertNull(repos.getLesson(excused)?.paidOn)
        assertEquals("Marked 1 lesson paid", posted.single().text)
    }

    @Test
    fun `dashboard summarises the month and lists who owes`() = runTest {
        val maria = addStudent("Maria")
        val nikos = addStudent("Nikos")
        repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        repos.saveLesson(lesson(maria, "2026-03-12", 1500, paidOn = "2026-03-12"))
        repos.saveLesson(lesson(nikos, "2026-02-20", 3000))

        val viewModel = DashboardViewModel(repos, repos, today = LocalDate.parse("2026-03-15"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val state = viewModel.state.value
        assertEquals(YearMonth.of(2026, 3), state.month)
        assertEquals(Money(3500), state.summary.earned)
        assertEquals(Money(1500), state.summary.paid)
        assertEquals(Money(5000), state.outstanding)
        assertEquals(listOf("Nikos", "Maria"), state.debtors.map { it.name })

        viewModel.previousMonth()
        assertEquals(Money(3000), viewModel.state.value.summary.earned)
    }

    @Test
    fun `home counts each section and dims the empty ones`() = runTest {
        val viewModel = homeViewModel(today = "2026-03-15")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val empty = viewModel.state.value
        assertFalse(empty.loading)
        assertTrue(empty.studentsEmpty && empty.groupsEmpty && empty.lessonsEmpty && empty.statementsEmpty)
        assertFalse("no student to log a lesson for", empty.canLogLesson)

        val maria = addStudent("Maria")
        repos.saveStudent(Student(firstName = "Old", rate = Rate(Money(2000), RateType.HOURLY), archived = true))
        repos.saveGroup(StudentGroup(name = "B1", rate = Rate(Money(1200), RateType.HOURLY)))
        val a = repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        val b = repos.saveLesson(lesson(maria, "2026-03-11", 1500))
        repos.saveLesson(lesson(maria, "2026-03-12", 1000))
        createInvoice(maria, listOf(a, b), LocalDate.parse("2026-03-31"))

        val state = viewModel.state.value
        assertEquals("archived students are not counted", 1, state.activeStudents)
        assertEquals(1, state.activeGroups)
        assertEquals(3, state.lessons)
        assertEquals(1, state.uninvoicedLessons)
        assertEquals(1, state.statements)
        assertEquals(1, state.unpaidStatements)
        assertEquals(Money(4500), state.outstanding)
        assertFalse(state.studentsEmpty || state.groupsEmpty || state.lessonsEmpty || state.statementsEmpty)
        assertTrue(state.canLogLesson)

        repos.setPaid(listOf(a, b), LocalDate.parse("2026-04-01"))
        assertEquals(0, viewModel.state.value.unpaidStatements)
    }

    @Test
    fun `home counts overdue statements`() = runTest {
        val maria = addStudent("Maria")
        val a = repos.saveLesson(lesson(maria, "2026-02-10", 2000))
        createInvoice(maria, listOf(a), LocalDate.parse("2026-02-28"))
        repos.saveLesson(lesson(maria, "2026-03-10", 2000)).let { createInvoice(maria, listOf(it), LocalDate.parse("2026-03-14")) }
        val viewModel = homeViewModel(today = "2026-03-15")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        assertEquals("February's is overdue, March's is not due yet", 1, viewModel.state.value.overdueStatements)
        assertEquals(2, viewModel.state.value.unpaidStatements)

        repos.setPaid(listOf(a), LocalDate.parse("2026-03-15"))
        assertEquals(0, viewModel.state.value.overdueStatements)
    }

    @Test
    fun `students flag overdue rows`() = runTest {
        val maria = addStudent("Maria")
        addStudent("Nikos")
        createInvoice(maria, listOf(repos.saveLesson(lesson(maria, "2026-02-10", 2000))), LocalDate.parse("2026-02-28"))
        val viewModel = StudentsViewModel(repos, repos, repos) { LocalDate.parse("2026-03-15") }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val rows = viewModel.state.value.rows.associate { it.student.firstName to it.overdue }
        assertEquals(mapOf("Maria" to true, "Nikos" to false), rows)
        assertEquals(1, viewModel.state.value.overdueStudents)
    }

    private val reminderState = InMemoryReminderStateRepository()

    private fun homeViewModel(today: String) = HomeViewModel(
        repos,
        repos,
        repos,
        repos,
        repos.settingsRepository,
        reminderState,
        today = { LocalDate.parse(today) },
    )

    @Test
    fun `home reminds at month end of the lessons not on a statement`() = runTest {
        val maria = addStudent("Maria")
        val nikos = addStudent("Nikos")
        val billed = repos.saveLesson(lesson(maria, "2026-03-02", 2000))
        repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        repos.saveLesson(lesson(maria, "2026-03-12", 2000))
        repos.saveLesson(lesson(nikos, "2026-03-20", 2000))
        repos.saveLesson(lesson(nikos, "2026-02-20", 2000))
        createInvoice(maria, listOf(billed), LocalDate.parse("2026-03-02"))

        val viewModel = homeViewModel(today = "2026-03-29")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        assertEquals(ReminderPrompt(YearMonth.of(2026, 3), lessons = 3, students = 2), viewModel.state.value.reminder)
    }

    @Test
    fun `home does not remind before the window or when the month is all on statements`() = runTest {
        val maria = addStudent("Maria")
        val billed = repos.saveLesson(lesson(maria, "2026-03-10", 2000))

        val early = homeViewModel(today = "2026-03-20")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { early.state.collect {} }
        assertNull("2026's Easter month starts on the 21st", early.state.value.reminder)

        val due = homeViewModel(today = "2026-03-21")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { due.state.collect {} }
        assertEquals(1, due.state.value.reminder?.lessons)

        createInvoice(maria, listOf(billed), LocalDate.parse("2026-03-21"))
        assertNull("nothing left to bill", due.state.value.reminder)

        repos.settingsRepository.update { it.copy(reminders = it.reminders.copy(enabled = false)) }
        repos.saveLesson(lesson(maria, "2026-03-21", 2000))
        assertNull("reminders off", due.state.value.reminder)
    }

    @Test
    fun `remind me tomorrow snoozes and done for this month stops the month`() = runTest {
        val maria = addStudent("Maria")
        repos.saveLesson(lesson(maria, "2026-03-10", 2000))

        val snoozing = homeViewModel(today = "2026-03-29")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { snoozing.state.collect {} }
        snoozing.remindTomorrow()
        assertNull(snoozing.state.value.reminder)
        assertEquals(LocalDate.parse("2026-03-30"), reminderState.state.value.snoozedUntil)

        val sameDay = homeViewModel(today = "2026-03-29")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sameDay.state.collect {} }
        assertNull("snoozed until tomorrow", sameDay.state.value.reminder)

        // Carried over into April's first days, then marked done.
        val nextDay = homeViewModel(today = "2026-04-02")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { nextDay.state.collect {} }
        assertEquals(YearMonth.of(2026, 3), nextDay.state.value.reminder?.month)
        nextDay.doneForMonth()
        assertNull(nextDay.state.value.reminder)
        assertEquals(YearMonth.of(2026, 3), reminderState.state.value.doneFor)

        val later = homeViewModel(today = "2026-04-03")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { later.state.collect {} }
        assertNull("March is done", later.state.value.reminder)
    }

    @Test
    fun `dismissing the reminder only hides it on this home`() = runTest {
        val maria = addStudent("Maria")
        repos.saveLesson(lesson(maria, "2026-03-10", 2000))

        val viewModel = homeViewModel(today = "2026-03-30")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.dismissReminder()

        assertNull(viewModel.state.value.reminder)
        assertEquals(ReminderState(), reminderState.state.value)
        val reopened = homeViewModel(today = "2026-03-30")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reopened.state.collect {} }
        assertEquals(YearMonth.of(2026, 3), reopened.state.value.reminder?.month)
    }

    @Test
    fun `lesson fee follows the rate until it is typed by hand`() = runTest {
        val maria = addStudent("Maria")
        val viewModel = LessonEditViewModel(
            lessonId = 0,
            initialStudentId = maria,
            students = repos,
            groups = repos,
            lessons = repos,
            settings = repos.settingsRepository,
            now = { LocalDateTime.parse("2026-03-10T17:20") },
        )

        assertEquals("20.00", viewModel.state.value.fee)
        viewModel.setDuration("90")
        assertEquals("30.00", viewModel.state.value.fee)
        viewModel.setFee("25")
        viewModel.setDuration("45")
        assertEquals("25", viewModel.state.value.fee)

        viewModel.save()

        assertTrue(viewModel.state.value.done)
        val saved = repos.lessons.single()
        assertEquals(Money(2500), saved.fee)
        assertEquals(45, saved.durationMinutes)
        assertEquals(LocalTime.of(17, 0), saved.startTime)
    }

    @Test
    fun `invoiced lessons open read-only`() = runTest {
        val maria = addStudent("Maria")
        val id = repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        createInvoice(maria, listOf(id), LocalDate.parse("2026-03-31"))

        val viewModel = LessonEditViewModel(id, 0, repos, repos, repos, repos.settingsRepository)
        viewModel.setDuration("30")
        viewModel.setNotes("homework")

        assertTrue(viewModel.state.value.locked)
        assertEquals("60", viewModel.state.value.duration)
        viewModel.save()
        assertEquals("homework", repos.getLesson(id)?.notes)
        assertEquals(60, repos.getLesson(id)?.durationMinutes)
    }

    @Test
    fun `lessons selection reschedules and marks paid in bulk`() = runTest {
        val maria = addStudent("Maria")
        val a = repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        val b = repos.saveLesson(lesson(maria, "2026-03-12", 2000))
        val viewModel = LessonsViewModel(repos, repos, repos, today = { LocalDate.parse("2026-03-15") })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.toggleSelection(a)
        viewModel.toggleSelection(b)
        assertEquals(setOf(a, b), viewModel.state.value.selected)
        assertTrue(viewModel.state.value.canReschedule)

        viewModel.rescheduleSelected(LocalDate.parse("2026-03-20"), LocalTime.of(9, 0))
        assertEquals(emptySet<Long>(), viewModel.state.value.selected)
        assertEquals(setOf(LocalDate.parse("2026-03-20")), repos.lessons.map { it.date }.toSet())
        assertEquals(setOf(LocalTime.of(9, 0)), repos.lessons.map { it.startTime }.toSet())

        viewModel.toggleSelection(a)
        viewModel.markSelectedPaid()
        assertEquals(LocalDate.parse("2026-03-15"), repos.getLesson(a)?.paidOn)
        assertNull(repos.getLesson(b)?.paidOn)

        viewModel.toggleSelection(a)
        assertFalse("paid lessons cannot be rescheduled", viewModel.state.value.canReschedule)
        viewModel.previousMonth()
        assertEquals(emptySet<Long>(), viewModel.state.value.selected)
    }

    @Test
    fun `a saved fee follows the length only while it still matches the rate`() = runTest {
        val maria = addStudent("Maria")
        val fromRate = repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        val byHand = repos.saveLesson(lesson(maria, "2026-03-11", 2500))

        val following = LessonEditViewModel(fromRate, 0, repos, repos, repos, repos.settingsRepository)
        following.setDuration("90")
        assertEquals("30.00", following.state.value.fee)

        val fixed = LessonEditViewModel(byHand, 0, repos, repos, repos, repos.settingsRepository)
        fixed.setDuration("90")
        assertEquals("25.00", fixed.state.value.fee)
    }

    @Test
    fun `marking a lesson absent clears its payment`() = runTest {
        val maria = addStudent("Maria")
        val id = repos.saveLesson(lesson(maria, "2026-03-10", 2000, paidOn = "2026-03-10"))
        val viewModel = LessonEditViewModel(id, 0, repos, repos, repos, repos.settingsRepository)
        assertTrue(viewModel.state.value.paid)

        viewModel.setAbsent(true)
        viewModel.save()

        assertFalse(viewModel.state.value.paid)
        val saved = repos.getLesson(id)!!
        assertTrue(saved.absent)
        assertNull(saved.paidOn)
    }

    @Test
    fun `a late cancellation is charged, keeps its payment and saves its reason`() = runTest {
        val maria = addStudent("Maria")
        val id = repos.saveLesson(lesson(maria, "2026-03-10", 2000, paidOn = "2026-03-10"))
        val viewModel = LessonEditViewModel(id, 0, repos, repos, repos, repos.settingsRepository)

        viewModel.setAbsent(true)
        assertFalse("an excused absence is not charged", viewModel.state.value.charged)
        assertFalse(viewModel.state.value.paid)

        viewModel.setAbsenceCharge(AbsenceCharge.LATE_CANCELLATION)
        assertTrue(viewModel.state.value.charged)
        viewModel.setPaid(true)
        viewModel.setAbsenceNote("cancelled at 16:00")
        viewModel.save()

        val saved = repos.getLesson(id)!!
        assertTrue(saved.isLateCancellation)
        assertEquals("cancelled at 16:00", saved.absenceNote)
        assertEquals("the original payment date is kept", LocalDate.parse("2026-03-10"), saved.paidOn)
        assertEquals(Money(2000), saved.charge)
    }

    @Test
    fun `the absence charge is locked on a statement but the reason is not`() = runTest {
        val maria = addStudent("Maria")
        val id = repos.saveLesson(lesson(maria, "2026-03-10", 2000).copy(absent = true))
        createInvoice(maria, listOf(id), LocalDate.parse("2026-03-31"))
        val viewModel = LessonEditViewModel(id, 0, repos, repos, repos, repos.settingsRepository)

        viewModel.setAbsenceCharge(AbsenceCharge.LATE_CANCELLATION)
        viewModel.setAbsenceNote("ill")
        assertEquals(AbsenceCharge.NONE, viewModel.state.value.absenceCharge)
        viewModel.save()

        assertTrue(viewModel.state.value.done)
        assertEquals("ill", repos.getLesson(id)?.absenceNote)
        assertEquals(AbsenceCharge.NONE, repos.getLesson(id)?.absenceCharge)
    }

    @Test
    fun `group attendance logs present, absent and late cancelled members`() = runTest {
        val maria = addStudent("Maria")
        val nikos = addStudent("Nikos")
        val eleni = addStudent("Eleni")
        val groupId = repos.saveGroup(
            StudentGroup(
                name = "B1",
                rate = Rate(Money(1200), RateType.HOURLY),
                memberIds = setOf(maria, nikos, eleni),
            ),
        )
        val viewModel = GroupLessonViewModel(
            groupId,
            repos,
            repos,
            repos,
            repos.settingsRepository,
            now = LocalDateTime.parse("2026-03-10T17:20"),
        )

        viewModel.setPresence(nikos, Presence.ABSENT)
        viewModel.setReason(nikos, "ill")
        viewModel.setPresence(eleni, Presence.LATE_CANCEL)
        viewModel.setReason(eleni, " late ")
        val state = viewModel.state.value
        assertEquals(1, state.presentCount)
        assertEquals(2, state.chargedCount)
        assertEquals(Money(1200), state.feePerStudent)

        viewModel.save()

        assertTrue(viewModel.state.value.done)
        val rows = repos.lessons.associateBy { it.studentId }
        assertFalse(rows.getValue(maria).absent)
        assertTrue(rows.getValue(nikos).isExcused)
        assertEquals("ill", rows.getValue(nikos).absenceNote)
        assertTrue(rows.getValue(eleni).isLateCancellation)
        assertEquals("late", rows.getValue(eleni).absenceNote)
        assertEquals(Money(2400), repos.lessons.fold(Money.ZERO) { sum, it -> sum + it.charge })
    }

    @Test
    fun `a new statement selects every lesson on no statement, absences included`() = runTest {
        val maria = addStudent("Maria")
        val held = repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        val excused = repos.saveLesson(lesson(maria, "2026-03-11", 2000).copy(absent = true))
        val late = repos.saveLesson(
            lesson(maria, "2026-03-12", 1500).copy(absent = true, absenceCharge = AbsenceCharge.LATE_CANCELLATION),
        )
        val viewModel = CreateInvoiceViewModel(
            initialStudentId = maria,
            students = repos,
            groups = repos,
            lessons = repos,
            invoices = repos,
            renderer = InvoiceRenderer(repos, repos, repos.settingsRepository),
            settings = repos.settingsRepository,
            today = LocalDate.parse("2026-03-31"),
        )
        repos.settingsRepository.update { it.copy(business = it.business.copy(name = "Tutor")) }

        assertEquals(setOf(held, excused, late), viewModel.state.value.selected)
        assertEquals(Money(3500), viewModel.state.value.total)

        viewModel.create()

        val created = viewModel.state.value.created!!
        assertEquals(Money(3500), created.total)
        assertTrue(repos.lessons.all { it.invoiceId == created.id })
    }

    @Test
    fun `a statement of excused absences shows as paid at once`() = runTest {
        val maria = addStudent("Maria")
        val excused = repos.saveLesson(lesson(maria, "2026-03-10", 2000).copy(absent = true))
        createInvoice(maria, listOf(excused), LocalDate.parse("2026-03-31"))
        val viewModel = InvoicesViewModel(repos, repos, repos, repos.settingsRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val item = viewModel.state.value.items.single()
        assertEquals(Money.ZERO, item.invoice.total)
        assertTrue(item.paid)
    }

    @Test
    fun `students list filters by the search text and shows balances`() = runTest {
        val maria = addStudent("Maria")
        addStudent("Nikos")
        repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        val viewModel = StudentsViewModel(repos, repos, repos)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        assertEquals(listOf("Maria", "Nikos"), viewModel.state.value.rows.map { it.student.firstName })
        assertEquals(Money(2000), viewModel.state.value.rows.first().outstanding)

        viewModel.onQueryChange("nik")
        assertEquals(listOf("Nikos"), viewModel.state.value.rows.map { it.student.firstName })
    }

    @Test
    fun `invoices list shows paid once every lesson is paid`() = runTest {
        val maria = addStudent("Maria")
        val a = repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        val b = repos.saveLesson(lesson(maria, "2026-03-11", 2000))
        val invoice = createInvoice(maria, listOf(a, b), LocalDate.parse("2026-03-31"))
        val viewModel = InvoicesViewModel(repos, repos, repos, repos.settingsRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        repos.setPaid(listOf(a), LocalDate.parse("2026-04-01"))
        assertFalse(viewModel.state.value.items.single().paid)
        repos.setPaid(listOf(b), LocalDate.parse("2026-04-01"))
        assertTrue(viewModel.state.value.items.single().paid)

        viewModel.markVoid(invoice)
        assertFalse("a void invoice is never shown as paid", viewModel.state.value.items.single().paid)
    }

    @Test
    fun `a group with invoiced lessons is not deleted`() = runTest {
        val maria = addStudent("Maria")
        val groupId = repos.saveGroup(StudentGroup(name = "B1", rate = Rate(Money(1200), RateType.HOURLY)))
        val id = repos.saveLesson(lesson(maria, "2026-03-10", 1200).copy(groupId = groupId))
        createInvoice(maria, listOf(id), LocalDate.parse("2026-03-31"))
        val viewModel = GroupEditViewModel(groupId, repos, repos, repos)

        viewModel.delete()

        assertFalse(viewModel.state.value.done)
        assertTrue(viewModel.state.value.message!!.contains("Archive"))
        assertEquals(groupId, repos.getLesson(id)?.groupId)
    }

    private val stored get() = repos.settingsRepository.settings.value

    // Backup files are not exercised here, so the stubbed Context behind them is never used.
    private fun settingsViewModel() =
        SettingsViewModel(repos.settingsRepository, BackupFiles(ContextWrapper(null), repos))

    /** Runs the debounced save (a task due exactly at the new time needs runCurrent). */
    private fun TestScope.pauseTyping() {
        advanceTimeBy(SAVE_DEBOUNCE_MS)
        runCurrent()
    }

    private fun statementDetailsViewModel() = StatementDetailsViewModel(repos.settingsRepository)

    @Test
    fun `statement details save once typing pauses`() = runTest {
        val viewModel = statementDetailsViewModel()

        viewModel.onName("Dimitris")
        advanceTimeBy(SAVE_DEBOUNCE_MS - 1)
        viewModel.onName("Dimitris T. ")
        advanceTimeBy(SAVE_DEBOUNCE_MS - 1)
        assertEquals("nothing is saved while typing", "", stored.business.name)

        advanceTimeBy(1)
        runCurrent()
        assertEquals("Dimitris T.", stored.business.name)
        assertEquals("the typed text is left alone", "Dimitris T. ", viewModel.state.value.form.name)
    }

    @Test
    fun `an invalid lesson length keeps the stored value while the theme saves`() = runTest {
        val viewModel = settingsViewModel()

        viewModel.onMinutes("0")
        pauseTyping()
        viewModel.onTheme(ThemeMode.DARK)

        assertEquals(60, stored.defaultLessonMinutes)
        assertEquals(ThemeMode.DARK, stored.themeMode)
        assertTrue(viewModel.state.value.minutesError)

        viewModel.onMinutes("45")
        assertFalse(viewModel.state.value.minutesError)
        pauseTyping()

        assertEquals(45, stored.defaultLessonMinutes)
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `the theme saves at once with any typing still pending`() = runTest {
        val viewModel = settingsViewModel()

        viewModel.onMinutes("50")
        viewModel.onTheme(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, stored.themeMode)
        assertEquals(50, stored.defaultLessonMinutes)
        assertEquals(ThemeMode.DARK, viewModel.state.value.form.themeMode)
    }

    @Test
    fun `reminder switches save at once`() = runTest {
        val viewModel = settingsViewModel()

        viewModel.onMinutes("90")
        viewModel.onRemindEarlyJune(false)
        assertEquals(ReminderSettings(earlyJune = false), stored.reminders)
        assertEquals("pending typing is saved too", 90, stored.defaultLessonMinutes)

        viewModel.onRemindersEnabled(false)
        viewModel.onRemindEarlyEaster(false)
        viewModel.onRemindEarlyDecember(false)
        assertEquals(ReminderSettings(false, false, false, false), stored.reminders)
        assertEquals(stored.reminders, viewModel.state.value.form.reminders)
    }

    @Test
    fun `settings and statement details each save only their own fields`() = runTest {
        val settings = settingsViewModel()
        val details = statementDetailsViewModel()

        details.onName("Tutor")
        settings.onTheme(ThemeMode.LIGHT)
        pauseTyping()

        assertEquals("Tutor", stored.business.name)
        assertEquals(ThemeMode.LIGHT, stored.themeMode)
    }

    @Test
    fun `the show-address switch saves at once`() = runTest {
        val viewModel = statementDetailsViewModel()
        assertFalse(stored.showAddressOnStatements)

        viewModel.onAddress("1 Main St")
        viewModel.onShowAddressOnStatements(true)

        assertTrue(stored.showAddressOnStatements)
        assertEquals("pending typing is saved with it", "1 Main St", stored.business.address)
        assertTrue(viewModel.state.value.form.showAddressOnStatements)
    }

    @Test
    fun `flush saves pending typing without waiting`() = runTest {
        val viewModel = statementDetailsViewModel()

        viewModel.onEmail("t@example.com")
        viewModel.onIban("GR16 0110")
        viewModel.flush()

        assertEquals("t@example.com", stored.business.email)
        assertEquals("GR16 0110", stored.business.iban)

        repos.settingsRepository.update { it.copy(invoiceFooter = "Changed elsewhere") }
        pauseTyping()
        viewModel.flush()
        assertEquals("nothing left to save", "Changed elsewhere", stored.invoiceFooter)
    }

    @Test
    fun `the privacy notice waits for the name and uses the details as typed`() = runTest {
        val settings = settingsViewModel()
        val details = statementDetailsViewModel()
        assertNull(settings.privacyNotice(NoticeLanguage.EN))
        assertNull(details.privacyNotice(NoticeLanguage.EL))

        details.onName("Tutor")
        assertTrue("typed, not yet saved", details.privacyNotice(NoticeLanguage.EN)!!.contains("Tutor is the controller"))
        assertNull(settings.privacyNotice(NoticeLanguage.EN))

        pauseTyping()
        assertTrue(settings.privacyNotice(NoticeLanguage.EL)!!.contains("Tutor είναι ο υπεύθυνος επεξεργασίας"))
    }

    @Test
    fun `create asks for the name when it is blank`() = runTest {
        val maria = addStudent("Maria")
        repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        val viewModel = CreateInvoiceViewModel(
            maria, repos, repos, repos, repos,
            InvoiceRenderer(repos, repos, repos.settingsRepository), repos.settingsRepository,
        )

        viewModel.create()
        assertTrue(viewModel.state.value.askForDetails)
        assertNull("nothing is issued yet", viewModel.state.value.created)

        viewModel.saveDetailsAndCreate("", "GR16")
        assertTrue("a blank name keeps asking", viewModel.state.value.askForDetails)

        viewModel.saveDetailsAndCreate("Tutor", "GR16 0110")
        assertFalse(viewModel.state.value.askForDetails)
        assertEquals("Tutor", stored.business.name)
        assertEquals("GR16 0110", stored.business.iban)
        assertNotNull(viewModel.state.value.created)
    }

    @Test
    fun `statements list shows whether the statement details are set`() = runTest {
        val viewModel = InvoicesViewModel(repos, repos, repos, repos.settingsRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        assertEquals("", viewModel.state.value.businessName)

        repos.settingsRepository.update { it.copy(business = it.business.copy(name = "Tutor", iban = "GR16")) }
        assertEquals("Tutor", viewModel.state.value.businessName)
        assertTrue(viewModel.state.value.ibanSet)
    }
}
