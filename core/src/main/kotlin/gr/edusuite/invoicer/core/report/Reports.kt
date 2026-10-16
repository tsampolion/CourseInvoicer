package gr.edusuite.invoicer.core.report

import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.sumOfMoney
import java.time.LocalDate
import java.time.YearMonth

data class PeriodSummary(
    val lessonCount: Int,
    val absentCount: Int,
    val minutes: Int,
    val earned: Money,
    val paid: Money,
    val unpaid: Money,
)

data class StudentBalance(val studentId: Long, val outstanding: Money, val openLessons: Int)

object Reports {
    /** Totals for lessons dated within [from]..[to] (inclusive). Absences are counted but never earned. */
    fun summarize(lessons: List<Lesson>, from: LocalDate, to: LocalDate): PeriodSummary {
        val inRange = lessons.filter { it.date in from..to }
        val held = inRange.filterNot { it.absent }
        val earned = held.sumOfMoney { it.fee }
        val paid = held.filter { it.isPaid }.sumOfMoney { it.fee }
        return PeriodSummary(
            lessonCount = held.size,
            absentCount = inRange.size - held.size,
            minutes = held.sumOf { it.durationMinutes },
            earned = earned,
            paid = paid,
            unpaid = earned - paid,
        )
    }

    fun summarize(lessons: List<Lesson>, month: YearMonth): PeriodSummary =
        summarize(lessons, month.atDay(1), month.atEndOfMonth())

    /** Students who owe money, largest balance first. */
    fun balances(lessons: List<Lesson>): List<StudentBalance> =
        lessons.filter { it.isOutstanding }
            .groupBy { it.studentId }
            .map { (studentId, open) -> StudentBalance(studentId, open.sumOfMoney { it.charge }, open.size) }
            .sortedWith(compareByDescending<StudentBalance> { it.outstanding }.thenBy { it.studentId })

    fun outstanding(lessons: List<Lesson>): Money = lessons.filter { it.isOutstanding }.sumOfMoney { it.charge }
}
