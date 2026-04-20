package com.domain.core.result

import com.domain.core.error.DomainError
import kotlinx.coroutines.CancellationException

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
 *
 * Example — exhaustive handling:
 * ```kotlin
 * fun handleUser(result: DomainResult<User>) {
 *     when (result) {
 *         is DomainResult.Success -> println("Got user: ${result.value.name}")
 *         is DomainResult.Failure -> println("Error: ${result.error.message}")
 *     }
 * }
 * ```
 */
public sealed class DomainResult<out T> {

    /**
     * Represents a successful operation carrying [value].
     *
     * Example:
     * ```kotlin
     * val result: DomainResult<Int> = DomainResult.Success(42)
     * println(result.value) // 42
     * ```
     */
    public data class Success<out T>(val value: T) : DomainResult<T>()

    /**
     * Represents a failed operation carrying a typed [DomainError].
     *
     * Example:
     * ```kotlin
     * val result: DomainResult<Nothing> = DomainResult.Failure(
     *     DomainError.NotFound(resourceType = "User", id = "123")
     * )
     * println(result.error.message) // "Resource 'User' with id '123' not found."
     * ```
     */
    public data class Failure(val error: DomainError) : DomainResult<Nothing>()

    /** Returns `true` if this is [Success], `false` otherwise. */
    public val isSuccess: Boolean get() = this is Success

    /** Returns `true` if this is [Failure], `false` otherwise. */
    public val isFailure: Boolean get() = this is Failure
}

// ── Constructors ────────────────────────────────────────────────────────────

/**
 * Wraps any value into a [DomainResult.Success].
 *
 * Example:
 * ```kotlin
 * val result: DomainResult<String> = "hello".asSuccess()
 * println(result.getOrNull()) // "hello"
 * ```
 */
public fun <T> T.asSuccess(): DomainResult<T> = DomainResult.Success(this)

/**
 * Creates a [DomainResult.Failure] from a [DomainError].
 *
 * Example:
 * ```kotlin
 * val result = domainFailure(DomainError.Unauthorized())
 * println(result.isFailure) // true
 * ```
 */
public fun domainFailure(error: DomainError): DomainResult<Nothing> = DomainResult.Failure(error)

// ── Extraction ───────────────────────────────────────────────────────────────

/**
 * Returns the encapsulated value if this is [DomainResult.Success], or `null` otherwise.
 *
 * Example:
 * ```kotlin
 * val success: DomainResult<String> = "hello".asSuccess()
 * val failure: DomainResult<String> = domainFailure(DomainError.Unauthorized())
 *
 * println(success.getOrNull()) // "hello"
 * println(failure.getOrNull()) // null
 * ```
 */
public fun <T> DomainResult<T>.getOrNull(): T? =
    (this as? DomainResult.Success)?.value

/**
 * Returns the encapsulated [DomainError] if this is [DomainResult.Failure], or `null` otherwise.
 *
 * Example:
 * ```kotlin
 * val failure = domainFailure(DomainError.NotFound("User", "42"))
 * val error = failure.errorOrNull() // DomainError.NotFound
 *
 * val success = "ok".asSuccess()
 * val noError = success.errorOrNull() // null
 * ```
 */
public fun <T> DomainResult<T>.errorOrNull(): DomainError? =
    (this as? DomainResult.Failure)?.error

/**
 * Returns the encapsulated value if this is [DomainResult.Success], or the result of
 * [default] applied to the [DomainError] if this is [DomainResult.Failure].
 *
 * This function is `inline` to allow non-local returns inside [default].
 *
 * Example:
 * ```kotlin
 * val result: DomainResult<String> = domainFailure(DomainError.Unauthorized())
 * val value = result.getOrElse { error -> "fallback: ${error.message}" }
 * println(value) // "fallback: Unauthorized"
 * ```
 *
 * Example — non-local return:
 * ```kotlin
 * fun loadUser(result: DomainResult<User>): User {
 *     return result.getOrElse { return User.GUEST }
 * }
 * ```
 */
public inline fun <T> DomainResult<T>.getOrElse(default: (DomainError) -> T): T = when (this) {
    is DomainResult.Success -> value
    is DomainResult.Failure -> default(error)
}

// ── Transformation ───────────────────────────────────────────────────────────

/**
 * Transforms the encapsulated value with [transform] if this is [DomainResult.Success].
 * Returns the original [DomainResult.Failure] unchanged if this is a failure.
 *
 * Example:
 * ```kotlin
 * val result: DomainResult<Int> = 5.asSuccess()
 * val mapped: DomainResult<String> = result.map { "Value is $it" }
 * println(mapped.getOrNull()) // "Value is 5"
 *
 * val failure = domainFailure(DomainError.Unauthorized())
 * val stillFailure = failure.map { "never called" }
 * println(stillFailure.isFailure) // true
 * ```
 */
public inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(value))
    is DomainResult.Failure -> this
}

/**
 * Transforms the encapsulated value with [transform] if this is [DomainResult.Success],
 * where [transform] itself returns a [DomainResult]. Useful for chaining operations
 * that may independently fail.
 *
 * Example:
 * ```kotlin
 * fun parseAge(input: String): DomainResult<Int> =
 *     input.toIntOrNull()?.asSuccess()
 *         ?: domainFailure(DomainError.Validation("age", "must be a number"))
 *
 * fun validateAge(age: Int): DomainResult<Int> =
 *     if (age in 0..150) age.asSuccess()
 *     else domainFailure(DomainError.Validation("age", "out of range"))
 *
 * val result = parseAge("25").flatMap { validateAge(it) }
 * println(result.getOrNull()) // 25
 * ```
 */
public inline fun <T, R> DomainResult<T>.flatMap(transform: (T) -> DomainResult<R>): DomainResult<R> = when (this) {
    is DomainResult.Success -> transform(value)
    is DomainResult.Failure -> this
}

/**
 * Transforms the encapsulated [DomainError] with [transform] if this is [DomainResult.Failure].
 * Returns the original [DomainResult.Success] unchanged.
 *
 * Useful for enriching or re-classifying errors at layer boundaries.
 *
 * Example:
 * ```kotlin
 * val result: DomainResult<User> = domainFailure(
 *     DomainError.Infrastructure("timeout")
 * )
 * val enriched = result.mapError { error ->
 *     DomainError.Infrastructure("User fetch failed: ${error.message}")
 * }
 * ```
 */
public inline fun <T> DomainResult<T>.mapError(transform: (DomainError) -> DomainError): DomainResult<T> = when (this) {
    is DomainResult.Success -> this
    is DomainResult.Failure -> DomainResult.Failure(transform(error))
}

/**
 * Performs [action] on the encapsulated value if this is [DomainResult.Success].
 * Returns `this` unchanged for chaining.
 *
 * Example:
 * ```kotlin
 * repository.findById(userId)
 *     .onSuccess { user -> analytics.trackUserLoaded(user.id) }
 *     .onFailure { error -> logger.warn("Failed: ${error.message}") }
 * ```
 */
public inline fun <T> DomainResult<T>.onSuccess(action: (T) -> Unit): DomainResult<T> {
    if (this is DomainResult.Success) action(value)
    return this
}

/**
 * Performs [action] on the encapsulated [DomainError] if this is [DomainResult.Failure].
 * Returns `this` unchanged for chaining.
 *
 * Example:
 * ```kotlin
 * repository.findById(userId)
 *     .onFailure { error -> logger.error("Lookup failed: ${error.message}") }
 * ```
 */
public inline fun <T> DomainResult<T>.onFailure(action: (DomainError) -> Unit): DomainResult<T> {
    if (this is DomainResult.Failure) action(error)
    return this
}

/**
 * Exhaustively handles both branches and returns a single value.
 *
 * Design rationale:
 * - Mirrors `NetworkResult.fold` from the data SDK, making the bridge pattern
 *   consistent: `networkResult.fold(...)` → `domainResult.fold(...)`.
 * - Forces the caller to handle both cases — no forgotten failure branch.
 *
 * Example — ViewModel mapping:
 * ```kotlin
 * val uiState = getUserUseCase(userId).fold(
 *     onSuccess = { user -> UserUiState.Loaded(user.name, user.email) },
 *     onFailure = { error -> UserUiState.Error(error.message) },
 * )
 * ```
 *
 * Example — bridging from Data SDK:
 * ```kotlin
 * networkResult.fold(
 *     onSuccess = { dto -> mapper.map(dto).asSuccess() },
 *     onFailure = { networkError -> domainFailure(networkError.toDomainError()) },
 * )
 * ```
 */
public inline fun <T, R> DomainResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (DomainError) -> R,
): R = when (this) {
    is DomainResult.Success -> onSuccess(value)
    is DomainResult.Failure -> onFailure(error)
}

// ── Combination ──────────────────────────────────────────────────────────────

/**
 * Combines two [DomainResult] values into a single result using [transform].
 * Fails with the first [DomainError] encountered if either operand is a failure.
 *
 * Design rationale:
 * - Replaces deeply nested `flatMap` chains when multiple independent results
 *   need to be combined: `zip(a, b) { x, y -> ... }` reads linearly.
 * - Fail-fast: [b] is still evaluated (both are already computed values, not
 *   lazy), but the error of [this] is returned first if both fail.
 * - Does not allocate intermediate pairs or tuples.
 *
 * Example:
 * ```kotlin
 * val name: DomainResult<String> = "Alice".asSuccess()
 * val age: DomainResult<Int> = 30.asSuccess()
 *
 * val user = name.zip(age) { n, a -> User(name = n, age = a) }
 * println(user.getOrNull()) // User(name=Alice, age=30)
 * ```
 */
public inline fun <A, B, R> DomainResult<A>.zip(
    other: DomainResult<B>,
    transform: (A, B) -> R,
): DomainResult<R> = when {
    this is DomainResult.Success && other is DomainResult.Success -> transform(value, other.value).asSuccess()
    this is DomainResult.Failure -> this
    else -> other as DomainResult.Failure
}

/**
 * Combines three [DomainResult] values. See [zip] for design rationale.
 * No intermediate Pair is allocated: all three operands are inspected directly.
 *
 * Example:
 * ```kotlin
 * val name = "Alice".asSuccess()
 * val age = 30.asSuccess()
 * val email = "alice@example.com".asSuccess()
 *
 * val profile = name.zip(age, email) { n, a, e ->
 *     UserProfile(name = n, age = a, email = e)
 * }
 * ```
 */
public inline fun <A, B, C, R> DomainResult<A>.zip(
    b: DomainResult<B>,
    c: DomainResult<C>,
    transform: (A, B, C) -> R,
): DomainResult<R> = when {
    this is DomainResult.Success && b is DomainResult.Success && c is DomainResult.Success ->
        transform(value, b.value, c.value).asSuccess()
    this is DomainResult.Failure -> this
    b is DomainResult.Failure -> b
    else -> c as DomainResult.Failure
}

// ── Lifting ──────────────────────────────────────────────────────────────────

/**
 * Wraps a potentially throwing [block] into a [DomainResult].
 * Infrastructure errors should be caught here and mapped to [DomainError.Infrastructure]
 * before crossing the domain boundary. This function is intentionally not inline
 * to avoid exposing catch semantics as part of the public API surface.
 *
 * [CancellationException] is always re-thrown to preserve structured concurrency.
 *
 * Example — default mapping:
 * ```kotlin
 * val result: DomainResult<User> = runDomainCatching {
 *     api.fetchUser(id) // may throw IOException
 * }
 * // IOException → DomainError.Unknown(cause = IOException(...))
 * ```
 *
 * Example — custom error mapping:
 * ```kotlin
 * val result = runDomainCatching(
 *     errorMapper = { throwable ->
 *         DomainError.Infrastructure(
 *             detail = "API call failed: ${throwable.message}",
 *             cause = throwable,
 *         )
 *     }
 * ) {
 *     api.fetchUser(id)
 * }
 * ```
 */
public suspend fun <T> runDomainCatching(
    errorMapper: (Throwable) -> DomainError = { DomainError.Unknown(cause = it) },
    block: suspend () -> T,
): DomainResult<T> = try {
    block().asSuccess()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    DomainResult.Failure(errorMapper(e))
}
