package gr.edusuite.invoicer.core.model

/** A business rule was violated. The message is shown to the user as-is. */
class DomainException(message: String) : Exception(message)
