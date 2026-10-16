package gr.edusuite.invoicer.core

import gr.edusuite.invoicer.core.model.AbsenceCharge
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Rate
import gr.edusuite.invoicer.core.model.RateType
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.testing.InMemoryRepositories
import gr.edusuite.invoicer.core.usecase.CreateInvoice
import gr.edusuite.invoicer.core.usecase.InvoiceRenderer
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

fun euros(amount: String): Money = requireNotNull(Money.parse(amount)) { "bad amount $amount" }

fun hourly(amount: String) = Rate(euros(amount), RateType.HOURLY)

fun perLesson(amount: String) = Rate(euros(amount), RateType.PER_LESSON)

fun student(firstName: String = "Maria", rate: Rate = hourly("20"), id: Long = 0) =
    Student(id = id, firstName = firstName, lastName = "Papadopoulou", rate = rate)

fun lesson(
    studentId: Long,
    date: String = "2026-03-10",
    fee: String = "20",
    minutes: Int = 60,
    absent: Boolean = false,
    paidOn: String? = null,
    id: Long = 0,
    absenceCharge: AbsenceCharge = AbsenceCharge.NONE,
    absenceNote: String = "",
) = Lesson(
    id = id,
    studentId = studentId,
    date = LocalDate.parse(date),
    startTime = LocalTime.of(17, 0),
    durationMinutes = minutes,
    fee = euros(fee),
    absent = absent,
    paidOn = paidOn?.let(LocalDate::parse),
    absenceCharge = absenceCharge,
    absenceNote = absenceNote,
)

/** A late cancellation: absent, charged the full fee. */
fun lateCancel(studentId: Long, fee: String = "20", paidOn: String? = null, note: String = "", id: Long = 0) =
    lesson(
        studentId,
        fee = fee,
        absent = true,
        paidOn = paidOn,
        id = id,
        absenceCharge = AbsenceCharge.LATE_CANCELLATION,
        absenceNote = note,
    )

/** Renders documents in a fixed locale so assertions do not depend on the machine. */
fun InMemoryRepositories.renderer() = InvoiceRenderer(this, this, settingsRepository) { Locale.UK }

fun InMemoryRepositories.createInvoiceUseCase() = CreateInvoice(this, this, renderer())
