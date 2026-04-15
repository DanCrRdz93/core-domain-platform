package com.domain.core.policy

import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure

/**
 * Contract for a domain policy — a named business rule that evaluates
 * whether a given context satisfies an invariant.
 *
 * Design rationale:
 * - [Validator] handles syntactic/structural constraints (not blank, max length, range).
 *   [DomainPolicy] handles semantic business rules that require richer context,
 *   often involving multiple fields or aggregate state (e.g., "can a user be promoted",
 *   "is this transition allowed from the current state").
 * - The distinction prevents validators from bloating with business logic and
 *   policies from degrading into field-level checks.
 * - `fun interface` keeps instantiation lightweight; a policy is a single rule.
 *   Compose policies with [and] / [or] rather than building god-policy classes.
 * - Returns [DomainResult<Unit>]: success means the policy is satisfied;
 *   failure carries a typed [com.domain.core.error.DomainError] explaining why not.
 * - Policies are pure by default. If a policy requires I/O (e.g., checking
 *   an external state), implement [SuspendDomainPolicy] instead.
 *
 * [C] — the context type the policy evaluates (an aggregate, a command, a value object).
 */
public fun interface DomainPolicy<in C> {
    public fun evaluate(context: C): DomainResult<Unit>
}

/**
 * Policy variant for rules that require async evaluation —
 * e.g., checking a repository-backed constraint before a state transition.
 *
 * Design rationale:
 * - Kept separate from [DomainPolicy] to make it explicit at the call site
 *   that this rule has an I/O cost and must be invoked in a coroutine scope.
 * - Callers that accept [DomainPolicy] remain pure; callers that accept
 *   [SuspendDomainPolicy] are explicitly async. This prevents accidental
 *   I/O inside synchronous evaluation paths.
 */
public fun interface SuspendDomainPolicy<in C> {
    public suspend fun evaluate(context: C): DomainResult<Unit>
}

// ── Combinators ───────────────────────────────────────────────────────────────

/**
 * Returns a policy that is satisfied only when both [this] and [other] are satisfied.
 * Evaluation is fail-fast: [other] is not evaluated if [this] fails.
 */
public infix fun <C> DomainPolicy<C>.and(other: DomainPolicy<C>): DomainPolicy<C> =
    DomainPolicy { context ->
        val first = evaluate(context)
        if (first.isFailure) first else other.evaluate(context)
    }

/**
 * Returns a policy that is satisfied when either [this] or [other] is satisfied.
 * [other] is only evaluated when [this] fails.
 */
public infix fun <C> DomainPolicy<C>.or(other: DomainPolicy<C>): DomainPolicy<C> =
    DomainPolicy { context ->
        val first = evaluate(context)
        if (first.isSuccess) first else other.evaluate(context)
    }

/**
 * Returns a policy that is satisfied when [this] is NOT satisfied, and vice versa.
 *
 * [onSatisfied] is called only when the original policy passes (and the negated
 * policy should therefore fail), producing the [DomainError] that describes why
 * the negation is violated. This signature prevents the accidental mistake of
 * passing a [DomainResult.Success] as the failure result.
 */
public fun <C> DomainPolicy<C>.negate(
    onSatisfied: () -> DomainError,
): DomainPolicy<C> = DomainPolicy { context ->
    if (evaluate(context).isFailure) Unit.asSuccess() else domainFailure(onSatisfied())
}
