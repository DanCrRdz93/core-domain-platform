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

/**
 * Runs all [validators] against [value] and accumulates every failure into a
 * single [DomainError.Validation] list, rather than stopping at the first failure.
 *
 * Design rationale:
 * - [andThen] is fail-fast: suitable for field-level chains where order matters.
 * - [validateAll] is fail-accumulate: suitable for form-level or command-level
 *   validation where the caller wants to report every violated rule at once.
 * - Returns [DomainResult.Success] only when all validators pass.
 * - Returns [DomainResult.Failure] with the first accumulated [DomainError.Validation]
 *   when at least one fails. For multi-error reporting, callers should iterate the
 *   full [errors] list returned by the accumulating variant below.
 * - Avoids allocating a list when all validators pass (early-exit optimization).
 */
public fun <T> validateAll(
    value: T,
    validators: List<Validator<T>>,
): DomainResult<Unit> {
    var firstError: DomainError? = null
    for (validator in validators) {
        val result = validator.validate(value)
        if (result is DomainResult.Failure && firstError == null) {
            firstError = result.error
        }
    }
    return if (firstError == null) Unit.asSuccess() else domainFailure(firstError)
}

/**
 * Overload that returns the full list of [DomainError] instances when any
 * validator fails. Returns an empty list on full success.
 *
 * Prefer this overload when the call site needs to present all violations
 * simultaneously (e.g., form submission, command pre-validation).
 */
public fun <T> collectValidationErrors(
    value: T,
    validators: List<Validator<T>>,
): List<DomainError> = validators
    .mapNotNull { (it.validate(value) as? DomainResult.Failure)?.error }

// ── Primitive validators ──────────────────────────────────────────────────────

/**
 * Validates that a [String] is not blank.
 */
public fun notBlankValidator(field: String): Validator<String> = Validator { value ->
    if (value.isNotBlank()) Unit.asSuccess()
    else domainFailure(DomainError.Validation(field = field, detail = "must not be blank."))
}

/**
 * Validates that a [String] does not exceed [maxLength] characters.
 */
public fun maxLengthValidator(field: String, maxLength: Int): Validator<String> = Validator { value ->
    if (value.length <= maxLength) Unit.asSuccess()
    else domainFailure(
        DomainError.Validation(
            field = field,
            detail = "must not exceed $maxLength characters.",
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
            detail = "must have at least $minLength characters.",
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
            detail = "must be between $min and $max.",
        )
    )
}
