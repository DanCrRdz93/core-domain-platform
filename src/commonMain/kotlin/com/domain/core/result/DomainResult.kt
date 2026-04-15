package com.domain.core.result

import com.domain.core.error.DomainError

/**
 * A domain-owned Result type that explicitly models success or failure
 * using [DomainError] rather than arbitrary [Throwable].
 *
 * Design rationale:
 * - Kotlin's stdlib [Result] uses [Throwable] for failure, which leaks infrastructure
 *   semantics into the domain layer. [DomainResult] keeps failure strictly typed.
 * - Implemented as a sealed class (not inline/value class) for clarity and exhaustive
 *   `when` expressions without boxing concerns at this layer.
 * - Extension functions replace inheritance for transformation logic, keeping
 *   subclasses data-only and avoiding virtual dispatch overhead.
 */
public sealed class DomainResult<out T> {

    public data class Success<out T>(val value: T) : DomainResult<T>()

    public data class Failure(val error: DomainError) : DomainResult<Nothing>()

    public val isSuccess: Boolean get() = this is Success
    public val isFailure: Boolean get() = this is Failure
}

// ── Constructors ────────────────────────────────────────────────────────────

public fun <T> T.asSuccess(): DomainResult<T> = DomainResult.Success(this)

public fun domainFailure(error: DomainError): DomainResult<Nothing> = DomainResult.Failure(error)

// ── Extraction ───────────────────────────────────────────────────────────────

public fun <T> DomainResult<T>.getOrNull(): T? =
    (this as? DomainResult.Success)?.value

public fun <T> DomainResult<T>.errorOrNull(): DomainError? =
    (this as? DomainResult.Failure)?.error

public fun <T> DomainResult<T>.getOrElse(default: (DomainError) -> T): T = when (this) {
    is DomainResult.Success -> value
    is DomainResult.Failure -> default(error)
}

// ── Transformation ───────────────────────────────────────────────────────────

public inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(value))
    is DomainResult.Failure -> this
}

public inline fun <T, R> DomainResult<T>.flatMap(transform: (T) -> DomainResult<R>): DomainResult<R> = when (this) {
    is DomainResult.Success -> transform(value)
    is DomainResult.Failure -> this
}

public inline fun <T> DomainResult<T>.mapError(transform: (DomainError) -> DomainError): DomainResult<T> = when (this) {
    is DomainResult.Success -> this
    is DomainResult.Failure -> DomainResult.Failure(transform(error))
}

public inline fun <T> DomainResult<T>.onSuccess(action: (T) -> Unit): DomainResult<T> {
    if (this is DomainResult.Success) action(value)
    return this
}

public inline fun <T> DomainResult<T>.onFailure(action: (DomainError) -> Unit): DomainResult<T> {
    if (this is DomainResult.Failure) action(error)
    return this
}

// ── Lifting ──────────────────────────────────────────────────────────────────

/**
 * Wraps a potentially throwing [block] into a [DomainResult].
 * Infrastructure errors should be caught here and mapped to [DomainError.Infrastructure]
 * before crossing the domain boundary. This function is intentionally not inline
 * to avoid exposing catch semantics as part of the public API surface.
 */
public suspend fun <T> runDomainCatching(
    errorMapper: (Throwable) -> DomainError = { DomainError.Unknown(cause = it) },
    block: suspend () -> T,
): DomainResult<T> = try {
    block().asSuccess()
} catch (e: Throwable) {
    DomainResult.Failure(errorMapper(e))
}
