package gr.edusuite.invoicer.core.report

import gr.edusuite.invoicer.core.euros
import gr.edusuite.invoicer.core.lesson
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class ReportsTest {
    private val lessons = listOf(
        lesson(studentId = 1, date = "2026-03-01", fee = "20", paidOn = "2026-03-02"),
        lesson(studentId = 1, date = "2026-03-15", fee = "30", minutes = 90),
        lesson(studentId = 2, date = "2026-03-31", fee = "15", absent = true),
        lesson(studentId = 2, date = "2026-03-20", fee = "15"),
        lesson(studentId = 3, date = "2026-04-01", fee = "50"),
        lesson(studentId = 3, date = "2026-02-28", fee = "0"),
    )

    @Test
    fun `month summary counts held lessons and splits paid from unpaid`() {
        val summary = Reports.summarize(lessons, YearMonth.of(2026, 3))

        assertEquals(3, summary.lessonCount)
        assertEquals(1, summary.absentCount)
        assertEquals(210, summary.minutes)
        assertEquals(euros("65"), summary.earned)
        assertEquals(euros("20"), summary.paid)
        assertEquals(euros("45"), summary.unpaid)
    }

    @Test
    fun `balances list debtors largest first and skip absences and free lessons`() {
        val balances = Reports.balances(lessons)

        assertEquals(listOf(3L, 1L, 2L), balances.map { it.studentId })
        assertEquals(listOf(euros("50"), euros("30"), euros("15")), balances.map { it.outstanding })
        assertEquals(listOf(1, 1, 1), balances.map { it.openLessons })
        assertEquals(euros("95"), Reports.outstanding(lessons))
    }
}
