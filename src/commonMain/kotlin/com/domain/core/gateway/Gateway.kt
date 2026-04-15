package com.domain.core.gateway

import com.domain.core.result.DomainResult

/**
 * Marker interface for domain gateway contracts.
 *
 * Design rationale:
 * - A [Gateway] models a dependency on an external capability that is NOT
 *   persistence (that is [Repository]). Examples: push notifications,
 *   auth token issuance, email dispatch, remote config, analytics sink.
 * - The domain defines the contract; infrastructure implements it.
 *   This enforces the Dependency Inversion Principle at the infra boundary.
 * - [Gateway] is intentionally separate from [Repository]:
 *   mixing both under one marker would obscure which dependency category
 *   an implementation belongs to, complicating DI registration and testing.
 * - No generics here: each gateway defines its own explicit, narrow contract.
 */
public interface Gateway

/**
 * Gateway contract for capabilities that produce a single async result.
 *
 * [I] — the input required by the external capability.
 * [O] — the output returned by the external capability, typed as [DomainResult]
 *        to keep error handling explicit at every call site.
 *
 * Design rationale:
 * - `suspend` is the only async primitive allowed at this boundary.
 * - [DomainResult] ensures that infrastructure failures are mapped to
 *   [com.domain.core.error.DomainError] before crossing into the domain.
 *   The domain must never receive raw exceptions from infrastructure.
 */
public interface SuspendGateway<in I, out O> : Gateway {
    public suspend fun execute(input: I): DomainResult<O>
}

/**
 * Gateway contract for fire-and-forget capabilities where the domain
 * does not require a typed response — e.g., analytics, logging sinks.
 *
 * Returns [DomainResult<Unit>] so that dispatch failures are still
 * observable at the call site when the caller cares.
 */
public interface CommandGateway<in I> : Gateway {
    public suspend fun dispatch(input: I): DomainResult<Unit>
}
