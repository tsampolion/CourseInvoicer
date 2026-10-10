package gr.eduinvoice.ui

import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.Money
import gr.eduinvoice.core.model.Rate
import gr.eduinvoice.core.model.RateType
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.testing.InMemoryRepositories
import gr.eduinvoice.core.usecase.CreateInvoice
import gr.eduinvoice.ui.home.HomeViewModel
import gr.eduinvoice.ui.lessons.LessonEditViewModel
import gr.eduinvoice.ui.lessons.LessonsViewModel
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
        CreateInvoice(repos, repos)(maria, listOf(id), LocalDate.parse("2026-03-31"))

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
}
