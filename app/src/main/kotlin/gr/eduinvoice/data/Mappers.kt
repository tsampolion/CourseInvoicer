package gr.eduinvoice.data

import android.content.ContentValues
import android.database.Cursor
import gr.eduinvoice.core.invoice.InvoiceDocumentCodec
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.Money
import gr.eduinvoice.core.model.Rate
import gr.eduinvoice.core.model.RateType
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.model.StudentGroup
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun LocalTime.toColumn(): String = format(TIME)

internal fun Cursor.toStudent() = Student(
    id = long("id"),
    firstName = string("first_name"),
    lastName = string("last_name"),
    className = string("class_name"),
    guardianName = string("guardian_name"),
    phone = string("phone"),
    email = string("email"),
    rate = Rate(Money(long("rate_cents")), RateType.valueOf(string("rate_type"))),
    notes = string("notes"),
    archived = bool("archived"),
)

internal fun Student.toValues(includeId: Boolean = false) = ContentValues().apply {
    if (includeId) put("id", id)
    put("first_name", firstName)
    put("last_name", lastName)
    put("class_name", className)
    put("guardian_name", guardianName)
    put("phone", phone)
    put("email", email)
    put("rate_cents", rate.amount.cents)
    put("rate_type", rate.type.name)
    put("notes", notes)
    put("archived", archived)
}

/** Members are loaded separately and attached by the caller. */
internal fun Cursor.toGroup() = StudentGroup(
    id = long("id"),
    name = string("name"),
    className = string("class_name"),
    rate = Rate(Money(long("rate_cents")), RateType.valueOf(string("rate_type"))),
    archived = bool("archived"),
)

internal fun StudentGroup.toValues(includeId: Boolean = false) = ContentValues().apply {
    if (includeId) put("id", id)
    put("name", name)
    put("class_name", className)
    put("rate_cents", rate.amount.cents)
    put("rate_type", rate.type.name)
    put("archived", archived)
}

internal fun Cursor.toLesson() = Lesson(
    id = long("id"),
    studentId = long("student_id"),
    groupId = longOrNull("group_id"),
    date = LocalDate.parse(string("date")),
    startTime = LocalTime.parse(string("start_time")),
    durationMinutes = int("duration_minutes"),
    fee = Money(long("fee_cents")),
    absent = bool("absent"),
    notes = string("notes"),
    paidOn = stringOrNull("paid_on")?.let(LocalDate::parse),
    invoiceId = longOrNull("invoice_id"),
)

internal fun Lesson.toValues(includeId: Boolean = false) = ContentValues().apply {
    if (includeId) put("id", id)
    put("student_id", studentId)
    put("group_id", groupId)
    put("date", date.toString())
    put("start_time", startTime.toColumn())
    put("duration_minutes", durationMinutes)
    put("fee_cents", fee.cents)
    put("absent", absent)
    put("notes", notes)
    put("paid_on", paidOn?.toString())
    put("invoice_id", invoiceId)
}

internal fun Cursor.toInvoice() = Invoice(
    id = long("id"),
    number = string("number"),
    studentId = long("student_id"),
    issueDate = LocalDate.parse(string("issue_date")),
    total = Money(long("total_cents")),
    notes = string("notes"),
    voidedOn = stringOrNull("voided_on")?.let(LocalDate::parse),
    document = stringOrNull("document")?.let(InvoiceDocumentCodec::decode),
)

internal fun Invoice.toValues(includeId: Boolean = false) = ContentValues().apply {
    if (includeId) put("id", id)
    put("number", number)
    put("student_id", studentId)
    put("issue_date", issueDate.toString())
    put("total_cents", total.cents)
    put("notes", notes)
    put("voided_on", voidedOn?.toString())
    put("document", document?.let(InvoiceDocumentCodec::encode))
}
