package com.domain.core.error

/**
 * Sealed hierarchy representing all error conditions expressible at the domain layer.
 *
 * Design rationale:
 * - Sealed ensures exhaustive handling at call sites without casting.
 * - No stack traces or platform types: domain errors carry only semantic meaning.
 * - [cause] is optional and typed as [Throwable] to avoid platform leakage while
 *   still allowing wrapping of infrastructure errors for debugging purposes.
 * - Subclasses are kept final (data class / object) to prevent uncontrolled extension.
 */
public sealed class DomainError(
    public open val message: String,
    public open val cause: Throwable? = null,
) {

    /**
     * The input provided does not satisfy domain invariants.
     * [field] identifies which field or parameter was invalid.
     */
    public data class Validation(
        val field: String,
        override val message: String,
    ) : DomainError(message)

    /**
     * The requested resource or aggregate does not exist.
     */
    public data class NotFound(
        val resourceType: String,
        val id: String,
    ) : DomainError(message = "Resource '$resourceType' with id '$id' not found.")

    /**
     * The caller is not authorised to perform the requested operation.
     */
    public data class Unauthorized(
        override val message: String = "Unauthorized",
    ) : DomainError(message)

    /**
     * The operation conflicts with the current state of the domain.
     * E.g., duplicate entity, invalid state transition.
     */
    public data class Conflict(
        override val message: String,
    ) : DomainError(message)

    /**
     * A downstream dependency (repository, gateway) failed in a way that
     * the domain cannot recover from. Wraps infrastructure errors without
     * exposing infrastructure types.
     */
    public data class Infrastructure(
        override val message: String,
        override val cause: Throwable? = null,
    ) : DomainError(message, cause)

    /**
     * A catch-all for unexpected domain conditions not covered above.
     * Usage should be rare; prefer a specific subclass.
     */
    public data class Unknown(
        override val message: String = "An unexpected error occurred.",
        override val cause: Throwable? = null,
    ) : DomainError(message, cause)
}
