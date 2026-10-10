package gr.eduinvoice.core.model

data class Student(
    val id: Long = 0,
    val firstName: String,
    val lastName: String = "",
    val className: String = "",
    val guardianName: String = "",
    val phone: String = "",
    val email: String = "",
    val rate: Rate,
    val notes: String = "",
    val archived: Boolean = false,
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}
