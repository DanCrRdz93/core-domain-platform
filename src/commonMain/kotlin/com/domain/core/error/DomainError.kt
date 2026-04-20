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
 *
 * Example — exhaustive `when` in a ViewModel:
 * ```kotlin
 * fun handleError(error: DomainError) = when (error) {
 *     is DomainError.Validation     -> showFieldError(error.field, error.detail)
 *     is DomainError.NotFound       -> showNotFound(error.resourceType)
 *     is DomainError.Unauthorized   -> navigateToLogin()
 *     is DomainError.Conflict       -> showConflict(error.detail)
 *     is DomainError.Infrastructure -> showRetryDialog(error.detail)
 *     is DomainError.Cancelled      -> { /* no-op, user navigated away */ }
 *     is DomainError.Unknown        -> showGenericError()
 * }
 * ```
 */
public sealed class DomainError(public val message: String) {

    /**
     * The input provided does not satisfy domain invariants.
     * [field] identifies which field or parameter was invalid.
     *
     * Example:
     * ```kotlin
     * val error = DomainError.Validation(field = "email", detail = "must contain @")
     * println(error.message) // "'email' must contain @"
     * ```
     */
    public data class Validation(
        val field: String,
        val detail: String,
    ) : DomainError(message = "'$field' $detail")

    /**
     * The requested resource or aggregate does not exist.
     *
     * Example:
     * ```kotlin
     * val error = DomainError.NotFound(resourceType = "Order", id = "ORD-999")
     * println(error.message) // "Resource 'Order' with id 'ORD-999' not found."
     * ```
     */
    public data class NotFound(
        val resourceType: String,
        val id: String,
    ) : DomainError(message = "Resource '$resourceType' with id '$id' not found.")

    /**
     * The caller is not authorised to perform the requested operation.
     *
     * Example:
     * ```kotlin
     * val error = DomainError.Unauthorized("Token expired")
     * println(error.message) // "Token expired"
     *
     * // Default message:
     * val defaultError = DomainError.Unauthorized()
     * println(defaultError.message) // "Unauthorized"
     * ```
     */
    public data class Unauthorized(
        val detail: String = "Unauthorized",
    ) : DomainError(message = detail)

    /**
     * The operation conflicts with the current state of the domain.
     * E.g., duplicate entity, invalid state transition.
     *
     * Example:
     * ```kotlin
     * val error = DomainError.Conflict("User with email 'a@b.com' already exists")
     * ```
     */
    public data class Conflict(
        val detail: String,
    ) : DomainError(message = detail)

    /**
     * A downstream dependency (repository, gateway) failed in a way that
     * the domain cannot recover from. Wraps infrastructure errors without
     * exposing infrastructure types.
     *
     * [cause] is `Throwable?` by design. When bridging from the data SDK,
     * pass `diagnostic?.cause` (the underlying exception), NOT the
     * `NetworkError` itself — `NetworkError` is not a `Throwable`.
     *
     * Example — bridging from data SDK:
     * ```kotlin
     * is NetworkError.Http -> DomainError.Infrastructure(
     *     detail = "HTTP ${statusCode}: ${body}",
     *     cause = diagnostic?.cause,
     * )
     * ```
     */
    public data class Infrastructure(
        val detail: String,
        val cause: Throwable? = null,
    ) : DomainError(message = detail)

    /**
     * The operation was cancelled — typically because the user navigated away,
     * the coroutine scope was cancelled, or a timeout policy triggered.
     *
     * Design rationale:
     * - Cancellation is **intentional**, not a failure. Mapping it as
     *   [Infrastructure] would misrepresent user-initiated or lifecycle-driven
     *   cancellations as infrastructure failures.
     * - This maps directly from `NetworkError.Cancelled` in the data SDK.
     * - Consumers should typically treat this as a no-op (suppress the error
     *   in the UI) rather than showing an error dialog.
     *
     * Example:
     * ```kotlin
     * is NetworkError.Cancelled -> DomainError.Cancelled("User cancelled the request")
     * ```
     */
    public data class Cancelled(
        val detail: String = "Operation was cancelled.",
    ) : DomainError(message = detail)

    /**
     * A catch-all for unexpected domain conditions not covered above.
     * Usage should be rare; prefer a specific subclass.
     *
     * [cause] is `Throwable?` — same contract as [Infrastructure].
     *
     * Example:
     * ```kotlin
     * val result = runDomainCatching { riskyOperation() }
     * // Unhandled exceptions become: DomainError.Unknown(cause = exception)
     * ```
     */
    public data class Unknown(
        val detail: String = "An unexpected error occurred.",
        val cause: Throwable? = null,
    ) : DomainError(message = detail)
}
