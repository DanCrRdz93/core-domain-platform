package com.domain.core.testing

import com.domain.core.di.DomainDependencies
import com.domain.core.error.DomainError
import com.domain.core.provider.ClockProvider
import com.domain.core.provider.IdProvider
import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure

// ── Canonical test doubles for the domain SDK core ───────────────────────────
//
// Rules enforced here:
//  1. No mocking framework — every double is a plain lambda or object expression.
//  2. Every double is deterministic — same input always produces same output.
//  3. Doubles expose only what tests need — no extra surface.
//  4. Naming convention:  fake<Type>   → returns a fixed success
//                         failing<Type>→ always returns a specific failure
//                         counting<Type>→ records call counts / sequences
//
// These are in `commonTest` — they are never part of the production API.

// ── ClockProvider doubles ─────────────────────────────────────────────────────

/** Fixed epoch millis used as the canonical "now" across all SDK tests. */
const val TEST_NOW_MILLIS: Long = 1_700_000_000_000L

/** A [ClockProvider] that always returns [TEST_NOW_MILLIS]. */
val fixedClock: ClockProvider = ClockProvider { TEST_NOW_MILLIS }

/** A [ClockProvider] that returns the provided [millis] on every call. */
fun clockAt(millis: Long): ClockProvider = ClockProvider { millis }

/** A [ClockProvider] that advances by [stepMillis] on each call starting at [startMillis]. */
fun advancingClock(startMillis: Long = TEST_NOW_MILLIS, stepMillis: Long = 1_000L): ClockProvider {
    var current = startMillis - stepMillis
    return ClockProvider {
        current += stepMillis
        current
    }
}

// ── IdProvider doubles ────────────────────────────────────────────────────────

/** A [IdProvider] that always returns [TEST_FIXED_ID]. */
const val TEST_FIXED_ID: String = "test-id-001"

/** A [IdProvider] that always returns [TEST_FIXED_ID]. */
val fixedId: IdProvider = IdProvider { TEST_FIXED_ID }

/** A [IdProvider] that always returns the given [id]. */
fun idOf(id: String): IdProvider = IdProvider { id }

/** A [IdProvider] that returns sequentially numbered ids: "id-1", "id-2", … */
fun sequentialIds(prefix: String = "id"): IdProvider {
    var counter = 0
    return IdProvider { "$prefix-${++counter}" }
}

// ── DomainDependencies doubles ────────────────────────────────────────────────

/** Canonical [DomainDependencies] for tests — fixed clock and fixed ID. */
val testDeps: DomainDependencies = DomainDependencies(
    clock = fixedClock,
    idProvider = fixedId,
)

/** [DomainDependencies] with a sequential ID provider — useful when multiple aggregates are created. */
fun testDepsWithSequentialIds(prefix: String = "id"): DomainDependencies = DomainDependencies(
    clock = fixedClock,
    idProvider = sequentialIds(prefix),
)

/** [DomainDependencies] with a custom clock — useful for time-sensitive logic. */
fun testDepsAt(millis: Long): DomainDependencies = DomainDependencies(
    clock = clockAt(millis),
    idProvider = fixedId,
)

// ── DomainError builders ─────────────────────────────────────────────────────

/** A [DomainError.Validation] with a sensible test default. */
fun validationError(field: String = "field", detail: String = "is invalid"): DomainError.Validation =
    DomainError.Validation(field = field, detail = detail)

/** A [DomainError.NotFound] with a sensible test default. */
fun notFoundError(resourceType: String = "Resource", id: String = "1"): DomainError.NotFound =
    DomainError.NotFound(resourceType = resourceType, id = id)

/** A [DomainError.Conflict] with a sensible test default. */
fun conflictError(detail: String = "conflict"): DomainError.Conflict =
    DomainError.Conflict(detail = detail)

/** A [DomainError.Infrastructure] with a sensible test default. */
fun infraError(detail: String = "infra failure", cause: Throwable? = null): DomainError.Infrastructure =
    DomainError.Infrastructure(detail = detail, cause = cause)

// ── DomainResult builders ─────────────────────────────────────────────────────

/** A [DomainResult.Failure] carrying a [validationError]. */
fun <T> validationFailure(field: String = "field", detail: String = "is invalid"): DomainResult<T> =
    domainFailure(validationError(field, detail))

/** A [DomainResult.Failure] carrying a [notFoundError]. */
fun <T> notFoundFailure(resourceType: String = "Resource", id: String = "1"): DomainResult<T> =
    domainFailure(notFoundError(resourceType, id))

/** A [DomainResult.Failure] carrying an [infraError]. */
fun <T> infraFailure(detail: String = "infra failure"): DomainResult<T> =
    domainFailure(infraError(detail))

// ── Assertion helpers ─────────────────────────────────────────────────────────

/**
 * Extracts the [DomainResult.Success.value] or throws an [AssertionError] with a
 * descriptive message. Avoids verbose casting in every test assertion.
 */
fun <T> DomainResult<T>.shouldBeSuccess(): T =
    when (this) {
        is DomainResult.Success -> value
        is DomainResult.Failure -> throw AssertionError("Expected Success but got Failure: $error")
    }

/**
 * Extracts the [DomainResult.Failure.error] or throws an [AssertionError].
 */
fun <T> DomainResult<T>.shouldBeFailure(): DomainError =
    when (this) {
        is DomainResult.Success -> throw AssertionError("Expected Failure but got Success: $value")
        is DomainResult.Failure -> error
    }

/**
 * Asserts the failure carries the expected [DomainError] subtype and returns it cast.
 */
inline fun <reified E : DomainError> DomainResult<*>.shouldFailWith(): E {
    val error = shouldBeFailure()
    return error as? E
        ?: throw AssertionError("Expected error of type ${E::class.simpleName} but got ${error::class.simpleName}: $error")
}
