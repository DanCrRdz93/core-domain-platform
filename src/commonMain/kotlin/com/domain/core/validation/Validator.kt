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
 *
 * Example — lambda-style:
 * ```kotlin
 * val emailValidator = Validator<String> { email ->
 *     if (email.contains("@")) Unit.asSuccess()
 *     else domainFailure(DomainError.Validation("email", "must contain @"))
 * }
 * ```
 *
 * Example — chaining validators:
 * ```kotlin
 * val nameValidator = notBlankValidator("name")
 *     .andThen(maxLengthValidator("name", 100))
 *
 * val result = nameValidator.validate("Alice") // Success
 * ```
 */
public fun interface Validator<in T> {
    public fun validate(value: T): DomainResult<Unit>
}

/**
 * Combines two [Validator] instances into a pipeline that short-circuits on failure.
 *
 * Design rationale:
 * - Fail-fast semantics: stops at the first failing validator and reports it.
 *   This is appropriate for field-level validation where order matters.
 * - If accumulate-all-errors semantics are needed, use [collectValidationErrors].
 *
 * Example:
 * ```kotlin
 * val usernameValidator = notBlankValidator("username")
 *     .andThen(minLengthValidator("username", 3))
 *     .andThen(maxLengthValidator("username", 20))
 *
 * val result = usernameValidator.validate("Al") // Failure: "must have at least 3 characters."
 * ```
 */
public fun <T> Validator<T>.andThen(other: Validator<T>): Validator<T> = Validator { value ->
    validate(value).let { result ->
        if (result.isSuccess) other.validate(value) else result
    }
}

/**
 * Runs all [validators] against [value] and returns the first error found.
 *
 * Design rationale:
 * - [andThen] is fail-fast: suitable for field-level chains where order matters.
 * - [validateAll] runs every validator but reports the first failure.
 * - For collecting all errors, use [collectValidationErrors] instead.
 *
 * Example:
 * ```kotlin
 * val result = validateAll(
 *     value = "ab",
 *     validators = listOf(
 *         notBlankValidator("username"),
 *         minLengthValidator("username", 3),
 *     ),
 * )
 * // result is Failure: "must have at least 3 characters."
 * ```
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
 * Returns all [DomainError] instances from validators that fail.
 * Returns an empty list when all validators pass.
 *
 * Prefer this when the call site needs to present all violations
 * simultaneously (e.g., form submission, command pre-validation).
 *
 * Example:
 * ```kotlin
 * val errors = collectValidationErrors(
 *     value = "",
 *     validators = listOf(
 *         notBlankValidator("name"),
 *         minLengthValidator("name", 2),
 *     ),
 * )
 * // errors = [Validation("name", "must not be blank."), Validation("name", "must have at least 2 characters.")]
 * ```
 */
public fun <T> collectValidationErrors(
    value: T,
    validators: List<Validator<T>>,
): List<DomainError> = validators
    .mapNotNull { (it.validate(value) as? DomainResult.Failure)?.error }

// ── Primitive validators ──────────────────────────────────────────────────────

/**
 * Validates that a [String] is not blank.
 *
 * Example:
 * ```kotlin
 * val validator = notBlankValidator("email")
 * validator.validate("")    // Failure: "'email' must not be blank."
 * validator.validate("a@b") // Success
 * ```
 */
public fun notBlankValidator(field: String): Validator<String> = Validator { value ->
    if (value.isNotBlank()) Unit.asSuccess()
    else domainFailure(DomainError.Validation(field = field, detail = "must not be blank."))
}

/**
 * Validates that a [String] does not exceed [maxLength] characters.
 *
 * Example:
 * ```kotlin
 * val validator = maxLengthValidator("bio", 200)
 * validator.validate("Short bio")         // Success
 * validator.validate("A".repeat(201))     // Failure: "must not exceed 200 characters."
 * ```
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
 *
 * Example:
 * ```kotlin
 * val validator = minLengthValidator("password", 8)
 * validator.validate("1234567")  // Failure: "must have at least 8 characters."
 * validator.validate("12345678") // Success
 * ```
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
 * Validates that a [Comparable] value falls within the closed range \[[min], [max]\].
 * General-purpose numeric or range validation.
 *
 * Example:
 * ```kotlin
 * val ageValidator = rangeValidator("age", min = 18, max = 120)
 * ageValidator.validate(25)  // Success
 * ageValidator.validate(15)  // Failure: "must be between 18 and 120."
 * ```
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
