package com.domain.core.validation

import com.domain.core.error.DomainError
import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure

/**
 * Contract for validating a value of type [T].
 *
 * Design rationale:
 * - `fun interface` allows lambda-style validators for simple rules, avoiding
 *   class proliferation for trivial constraints.
 * - Returns [DomainResult] rather than throwing or returning Boolean so that
 *   validation failure is always explicit and typed at call sites.
 * - Validators are pure functions: no I/O, no side effects, no state.
 *   If validation requires I/O (e.g., uniqueness check), use a [SuspendUseCase].
 */
public fun interface Validator<in T> {
    public fun validate(value: T): DomainResult<Unit>
}

/**
 * Combines multiple [Validator] instances into a single one that accumulates
 * all failures into a [DomainError.Validation] on the first failure found.
 *
 * Design rationale:
 * - Fail-fast semantics: stops at the first failing validator and reports it.
 *   This is appropriate for field-level validation where order matters.
 * - If accumulate-all-errors semantics are needed, implement a separate combinator
 *   to avoid cluttering this contract with two different strategies.
 */
public fun <T> Validator<T>.andThen(other: Validator<T>): Validator<T> = Validator { value ->
    validate(value).let { result ->
        if (result.isSuccess) other.validate(value) else result
    }
}

// ── Primitive validators ──────────────────────────────────────────────────────

/**
 * Validates that a [String] is not blank.
 */
public fun notBlankValidator(field: String): Validator<String> = Validator { value ->
    if (value.isNotBlank()) Unit.asSuccess()
    else domainFailure(DomainError.Validation(field = field, message = "'$field' must not be blank."))
}

/**
 * Validates that a [String] does not exceed [maxLength] characters.
 */
public fun maxLengthValidator(field: String, maxLength: Int): Validator<String> = Validator { value ->
    if (value.length <= maxLength) Unit.asSuccess()
    else domainFailure(
        DomainError.Validation(
            field = field,
            message = "'$field' must not exceed $maxLength characters.",
        )
    )
}

/**
 * Validates that a [String] has at least [minLength] characters.
 */
public fun minLengthValidator(field: String, minLength: Int): Validator<String> = Validator { value ->
    if (value.length >= minLength) Unit.asSuccess()
    else domainFailure(
        DomainError.Validation(
            field = field,
            message = "'$field' must have at least $minLength characters.",
        )
    )
}

/**
 * Validates that a [Comparable] value satisfies the given [predicate].
 * General-purpose numeric or range validation.
 */
public fun <T : Comparable<T>> rangeValidator(
    field: String,
    min: T,
    max: T,
): Validator<T> = Validator { value ->
    if (value in min..max) Unit.asSuccess()
    else domainFailure(
        DomainError.Validation(
            field = field,
            message = "'$field' must be between $min and $max.",
        )
    )
}
