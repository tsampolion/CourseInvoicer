package gr.edusuite.invoicer.ui

import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Rate
import gr.edusuite.invoicer.core.model.RateType
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.testing.InMemoryRepositories
import gr.edusuite.invoicer.core.usecase.CreateInvoice
import gr.edusuite.invoicer.core.usecase.InvoiceRenderer
import gr.edusuite.invoicer.ui.groups.GroupEditViewModel
import gr.edusuite.invoicer.ui.home.HomeViewModel
import gr.edusuite.invoicer.ui.invoices.InvoicesViewModel
import gr.edusuite.invoicer.ui.lessons.LessonEditViewModel
import gr.edusuite.invoicer.ui.lessons.LessonsViewModel
import gr.edusuite.invoicer.ui.students.StudentsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `home summarises the month and lists who owes`() = runTest {
        val maria = addStudent("Maria")
        val nikos = addStudent("Nikos")
        repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        repos.saveLesson(lesson(maria, "2026-03-12", 1500, paidOn = "2026-03-12"))
        repos.saveLesson(lesson(nikos, "2026-02-20", 3000))

        val viewModel = HomeViewModel(repos, repos, today = LocalDate.parse("2026-03-15"))
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
    fun `students list filters by the search text and shows balances`() = runTest {
        val maria = addStudent("Maria")
        addStudent("Nikos")
        repos.saveLesson(lesson(maria, "2026-03-10", 2000))
        val viewModel = StudentsViewModel(repos, repos)
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
        val viewModel = InvoicesViewModel(repos, repos, repos)
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
}
