package com.domain.core.gateway

import com.domain.core.result.DomainResult
import kotlinx.coroutines.flow.Flow

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
 *
 * Example — domain contract:
 * ```kotlin
 * interface AuthGateway : SuspendGateway<Credentials, AuthToken>
 * ```
 *
 * Example — data-layer implementation:
 * ```kotlin
 * class AuthGatewayImpl(private val api: AuthApi) : AuthGateway {
 *     override suspend fun execute(input: Credentials): DomainResult<AuthToken> =
 *         runDomainCatching { api.login(input.email, input.password) }
 *             .map { AuthToken(it.accessToken, it.expiresIn) }
 * }
 * ```
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
 *
 * Example:
 * ```kotlin
 * interface AnalyticsGateway : CommandGateway<AnalyticsEvent>
 *
 * // Call site (fire-and-forget):
 * analyticsGateway.dispatch(AnalyticsEvent.ScreenViewed("home"))
 * ```
 */
public interface CommandGateway<in I> : Gateway {
    public suspend fun dispatch(input: I): DomainResult<Unit>
}

/**
 * Gateway contract for capabilities that produce a reactive stream of results.
 *
 * [I] — the input / subscription parameter.
 * [O] — the output emitted over time.
 *
 * Design rationale:
 * - [SuspendGateway] produces a single value; [FlowGateway] produces many.
 *   Keeping them separate makes call-site intent explicit: "one value" vs "stream".
 * - Typical consumers: WebSocket feeds, SSE streams, [SessionController.state]
 *   from the data SDK, real-time price tickers, connectivity monitors.
 * - Each emission is wrapped in [DomainResult] so that transient errors
 *   can be reported without cancelling the stream.
 *
 * Example:
 * ```kotlin
 * interface PriceTickerGateway : FlowGateway<String, PriceTick>
 *
 * // Call site:
 * priceTickerGateway.observe("BTC-USD").collect { result ->
 *     result.onSuccess { tick -> updatePrice(tick) }
 * }
 * ```
 */
public interface FlowGateway<in I, out O> : Gateway {
    public fun observe(input: I): Flow<DomainResult<O>>
}

/**
 * No-params variant of [FlowGateway] — observe a global stream that
 * requires no subscription parameter.
 *
 * Typical consumers: session state changes, connectivity status,
 * unread notification count.
 *
 * Example:
 * ```kotlin
 * interface ConnectivityGateway : NoParamsFlowGateway<Boolean>
 *
 * // Call site:
 * connectivityGateway.observe().collect { result ->
 *     result.onSuccess { isOnline -> updateConnectionBanner(isOnline) }
 * }
 * ```
 */
public interface NoParamsFlowGateway<out O> : Gateway {
    public fun observe(): Flow<DomainResult<O>>
}
