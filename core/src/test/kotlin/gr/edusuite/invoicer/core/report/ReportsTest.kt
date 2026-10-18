package gr.edusuite.invoicer.core.report

import gr.edusuite.invoicer.core.euros
import gr.edusuite.invoicer.core.lateCancel
import gr.edusuite.invoicer.core.lesson
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReportsTest {
    private val lessons = listOf(
        lesson(studentId = 1, date = "2026-03-01", fee = "20", paidOn = "2026-03-02"),
        lesson(studentId = 1, date = "2026-03-15", fee = "30", minutes = 90),
        lesson(studentId = 2, date = "2026-03-31", fee = "15", absent = true),
        lesson(studentId = 2, date = "2026-03-20", fee = "15"),
        lateCancel(studentId = 2, fee = "15").copy(date = LocalDate.parse("2026-03-25")),
        lesson(studentId = 3, date = "2026-04-01", fee = "50"),
        lesson(studentId = 3, date = "2026-02-28", fee = "0"),
    )

    @Test
    fun `month summary counts held lessons, earns late cancellations and splits paid from unpaid`() {
        val summary = Reports.summarize(lessons, YearMonth.of(2026, 3))

        assertEquals(3, summary.lessonCount)
        assertEquals(2, summary.absentCount)
        assertEquals(1, summary.lateCancelCount)
        assertEquals("a cancelled lesson was not taught", 210, summary.minutes)
        assertEquals(euros("80"), summary.earned)
        assertEquals(euros("20"), summary.paid)
        assertEquals(euros("60"), summary.unpaid)
    }

    @Test
    fun `balances list debtors largest first, charge late cancellations and skip excused and free lessons`() {
        val balances = Reports.balances(lessons)

        assertEquals(listOf(3L, 1L, 2L), balances.map { it.studentId })
        assertEquals(listOf(euros("50"), euros("30"), euros("30")), balances.map { it.outstanding })
        assertEquals(listOf(1, 1, 2), balances.map { it.openLessons })
        assertEquals(euros("110"), Reports.outstanding(lessons))
    }
    @Test fun `attendance table pins charge and report totals before and after payment`() {
        data class Row(val lesson: gr.edusuite.invoicer.core.model.Lesson, val charge: String, val count: Int, val absent: Int, val late: Int)
        val rows = listOf(
            Row(lesson(studentId = 1, fee = "20"), "20", 1, 0, 0),
            Row(lesson(studentId = 1, fee = "20", absent = true), "0", 0, 1, 0),
            Row(lateCancel(studentId = 1, fee = "20"), "20", 0, 1, 1),
        )
        for (row in rows) {
            for (paid in listOf(false, true)) {
                val lesson = row.lesson.copy(paidOn = if (paid) LocalDate.of(2026, 3, 31) else null)
                assertEquals(row.toString(), euros(row.charge), lesson.charge)
                assertEquals(row.toString(), row.charge != "0" && !paid, lesson.isOutstanding)
                val summary = Reports.summarize(listOf(lesson), YearMonth.from(lesson.date))
                assertEquals(row.count, summary.lessonCount)
                assertEquals(row.absent, summary.absentCount)
                assertEquals(row.late, summary.lateCancelCount)
                assertEquals(euros(row.charge), summary.earned)
                assertEquals(euros(if (paid) row.charge else "0"), summary.paid)
                assertEquals(euros(if (paid) "0" else row.charge), summary.unpaid)
            }
        }
    }

}
