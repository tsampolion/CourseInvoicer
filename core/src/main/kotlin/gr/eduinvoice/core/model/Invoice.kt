package gr.eduinvoice.core.model

import java.time.LocalDate

data class Invoice(
    val id: Long = 0,
    val number: String,
    val studentId: Long,
    val issueDate: LocalDate,
    val total: Money,
    val notes: String = "",
)
