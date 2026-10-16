package gr.edusuite.invoicer.core.invoice

import gr.edusuite.invoicer.core.format.Formats
import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.InvoiceDocument
import gr.edusuite.invoicer.core.model.InvoicePayment
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Student

/**
 * Formats a statement's content from the current data; see [InvoiceDocument] for when it is frozen.
 * A statement is informational, not a fiscal document (R14, D20), and says so on every page.
 */
object InvoiceDocumentBuilder {
    const val TITLE = "ΕΝΗΜΕΡΩΤΙΚΗ ΚΑΤΑΣΤΑΣΗ ΜΑΘΗΜΑΤΩΝ / Lesson statement"

    /** Printed under the header and in every page footer (D20). An accountant should confirm the wording. */
    const val DISCLAIMER = "ΤΟ ΠΑΡΟΝ ΔΕΝ ΑΠΟΤΕΛΕΙ ΦΟΡΟΛΟΓΙΚΟ ΣΤΟΙΧΕΙΟ. Δεν είναι τιμολόγιο ούτε απόδειξη " +
        "παροχής υπηρεσιών κατά τον ν. 4308/2014 (ΕΛΠ) και δεν διαβιβάζεται στην ΑΑΔΕ (myDATA). " +
        "Για κάθε πληρωμή εκδίδεται χωριστά νόμιμη απόδειξη."

    const val EXCUSED_NOTE = "Absent — not charged"
    const val LATE_CANCELLATION_NOTE = "Late cancellation (under 24 h) — charged"

    fun build(
        invoice: Invoice,
        student: Student,
        lessons: List<Lesson>,
        groupNames: Map<Long, String>,
        settings: AppSettings,
        formats: Formats,
    ): InvoiceDocument {
        val business = settings.business
        val seller = listOf(
            business.name,
            business.address.takeIf { settings.showAddressOnStatements }.orEmpty(),
            listOf(business.phone, business.email).filter { it.isNotBlank() }.joinToString(" · "),
        ).flatMap { it.lines() }.filter { it.isNotBlank() }

        val billTo = listOf(
            student.fullName,
            student.guardianName.takeIf { it.isNotBlank() }?.let { "c/o $it" }.orEmpty(),
            student.phone,
            student.email,
        ).filter { it.isNotBlank() }

        val lines = lessons.sortedWith(compareBy({ it.date }, { it.startTime })).map { lesson ->
            val kind = lesson.groupId?.let { groupNames[it] }?.let { "Group lesson · $it" } ?: "Lesson"
            val description = listOf(kind, lesson.notes.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
            InvoiceDocument.Line(
                date = formats.date(lesson.date),
                description = description,
                duration = formats.duration(lesson.durationMinutes),
                amount = formats.money(lesson.charge),
                note = lesson.absenceLineNote(),
            )
        }

        val notes = buildList {
            invoice.notes.takeIf { it.isNotBlank() }?.let(::add)
            business.iban.takeIf { it.isNotBlank() }?.let { add("Payment by bank transfer to IBAN $it") }
            settings.invoiceFooter.takeIf { it.isNotBlank() }?.let(::add)
        }

        return InvoiceDocument(
            number = invoice.number,
            issueDate = formats.date(invoice.issueDate),
            seller = seller,
            billTo = billTo,
            lines = lines,
            total = formats.money(invoice.total),
            paid = InvoicePayment.of(lessons).isPaid,
            notes = notes,
            title = TITLE,
            disclaimer = DISCLAIMER,
        )
    }

    /** Why an absence line is or is not charged, with the reason if one was given. Null for held lessons. */
    private fun Lesson.absenceLineNote(): String? {
        val label = when {
            isExcused -> EXCUSED_NOTE
            isLateCancellation -> LATE_CANCELLATION_NOTE
            else -> return null
        }
        val reason = absenceNote.trim()
        return if (reason.isEmpty()) label else "$label: $reason"
    }
}
