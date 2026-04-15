package com.domain.core.error

/**
 * Sealed hierarchy representing all error conditions expressible at the domain layer.
 *
 * Design rationale:
 * - Sealed ensures exhaustive handling at call sites without casting.
 * - [message] is declared on the sealed parent as a non-open val: subclasses
 *   pass their message to the super constructor and do not redeclare it as a
 *   property, avoiding the `override val` anti-pattern on sealed hierarchies.
 * - [cause] is intentionally absent from the sealed base. Only [Infrastructure]
 *   and [Unknown] carry a cause — they are the only subtypes that wrap external
 *   exceptions. Exposing cause on [Validation], [NotFound], etc. would pollute
 *   pure domain errors with infrastructure semantics.
 * - Subclasses are final (data class) to prevent uncontrolled extension.
 */
public sealed class DomainError(public val message: String) {

    /**
     * The input provided does not satisfy domain invariants.
     * [field] identifies which field or parameter was invalid.
     */
    public data class Validation(
        val field: String,
        val detail: String,
    ) : DomainError(message = "'$field' $detail")

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
        val detail: String = "Unauthorized",
    ) : DomainError(message = detail)

    /**
     * The operation conflicts with the current state of the domain.
     * E.g., duplicate entity, invalid state transition.
     */
    public data class Conflict(
        val detail: String,
    ) : DomainError(message = detail)

    /**
     * A downstream dependency (repository, gateway) failed in a way that
     * the domain cannot recover from. Wraps infrastructure errors without
     * exposing infrastructure types.
     */
    public data class Infrastructure(
        val detail: String,
        val cause: Throwable? = null,
    ) : DomainError(message = detail)

    /**
     * A catch-all for unexpected domain conditions not covered above.
     * Usage should be rare; prefer a specific subclass.
     */
    public data class Unknown(
        val detail: String = "An unexpected error occurred.",
        val cause: Throwable? = null,
    ) : DomainError(message = detail)
}
