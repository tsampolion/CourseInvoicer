package gr.edusuite.invoicer.core.privacy

import gr.edusuite.invoicer.core.model.InvoiceDocument
import gr.edusuite.invoicer.core.model.Student

/** The name an erased student is left with (R17). */
const val ERASED_STUDENT = "Erased student"

/** The student with no personal data left: [ERASED_STUDENT], every contact field and the notes blank, archived. */
fun Student.anonymised(): Student = copy(
    firstName = ERASED_STUDENT,
    lastName = "",
    className = "",
    guardianName = "",
    phone = "",
    email = "",
    notes = "",
    archived = true,
)

/** True once [anonymised] has run, so the student is not offered for erasure again. */
val Student.isErased: Boolean
    get() = firstName == ERASED_STUDENT && lastName.isEmpty() && guardianName.isEmpty() && phone.isEmpty() &&
        email.isEmpty()

/**
 * The issued document with the student taken out (R17, the one exception to R12): the bill-to block
 * becomes [ERASED_STUDENT] and the notes go. Number, dates, lines and amounts stay, so numbering and past
 * totals are intact (R6). Free text the tutor typed about a lesson (after " — " in a description, after ":"
 * in an absence note, such as a reason for being ill) is cut, since it can describe the student.
 */
fun InvoiceDocument.anonymised(): InvoiceDocument = copy(
    billTo = listOf(ERASED_STUDENT),
    notes = emptyList(),
    lines = lines.map { line ->
        line.copy(
            description = line.description.substringBefore(" — "),
            note = line.note?.substringBefore(":"),
        )
    },
)
