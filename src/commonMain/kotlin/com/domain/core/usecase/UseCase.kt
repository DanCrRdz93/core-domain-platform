package com.domain.core.usecase

import com.domain.core.result.DomainResult
import kotlinx.coroutines.flow.Flow

/**
 * Synchronous, pure use case. Executes an in-memory computation with no
 * side effects and no I/O. Returns a [DomainResult] to allow validation or
 * business-rule failures without throwing.
 *
 * Design rationale:
 * - Named [PureUseCase] to make the no-I/O constraint explicit. A consumer
 *   who reaches for this type knows it is safe to call from any context,
 *   including UI threads, without blocking concerns.
 * - `fun interface` enables SAM-style instantiation for testing: no anonymous
 *   class boilerplate, no lambda type erasure issues.
 * - Single [invoke] operator keeps call sites clean: `useCase(params)`.
 * - [I] = input params type; [O] = output type.
 *   Use [Unit] for parameterless or side-effect-only variants.
 */
public fun interface PureUseCase<in I, out O> {
    public operator fun invoke(params: I): DomainResult<O>
}

/**
 * Suspending use case. For operations that are asynchronous but produce a
 * single value — e.g., submit a command, fetch once.
 *
 * Design rationale:
 * - `suspend` is the idiomatic KMP async primitive. No RxJava, no callbacks.
 * - Wrapping with [DomainResult] avoids throwing across coroutine boundaries,
 *   keeping error handling explicit at every call site.
 */
public fun interface SuspendUseCase<in I, out O> {
    public suspend operator fun invoke(params: I): DomainResult<O>
}

/**
 * Reactive use case. For operations that emit multiple values over time —
 * e.g., observe changes, stream events.
 *
 * Design rationale:
 * - Returns `Flow<DomainResult<O>>` instead of `Flow<O>` so individual
 *   emissions can carry errors without cancelling the entire stream.
 * - The Flow itself is not wrapped in [DomainResult]: cold stream setup
 *   failures are rare and best expressed as the flow throwing, which
 *   callers handle with `catch {}` operators.
 */
public fun interface FlowUseCase<in I, out O> {
    public operator fun invoke(params: I): Flow<DomainResult<O>>
}

/**
 * Convenience alias for use cases that take no parameters.
 * Avoids forcing callers to pass [Unit] explicitly.
 */
public fun interface NoParamsUseCase<out O> {
    public suspend operator fun invoke(): DomainResult<O>
}

/**
 * Convenience alias for reactive use cases that take no parameters.
 */
public fun interface NoParamsFlowUseCase<out O> {
    public operator fun invoke(): Flow<DomainResult<O>>
}
