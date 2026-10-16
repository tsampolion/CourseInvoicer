package gr.edusuite.invoicer.core.model

/** Students taught together. Each member is charged [rate] for every group lesson they attend. */
data class StudentGroup(
    val id: Long = 0,
    val name: String,
    val className: String = "",
    val rate: Rate,
    val memberIds: Set<Long> = emptySet(),
    val archived: Boolean = false,
)
