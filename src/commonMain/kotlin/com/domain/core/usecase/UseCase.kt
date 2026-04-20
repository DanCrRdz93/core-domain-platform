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
 *
 * Example — class implementation:
 * ```kotlin
 * class CalculateDiscount(
 *     private val policy: DomainPolicy<Order>,
 * ) : PureUseCase<Order, Double> {
 *     override fun invoke(params: Order): DomainResult<Double> {
 *         return policy.evaluate(params).map { params.total * 0.10 }
 *     }
 * }
 * ```
 *
 * Example — test stub (SAM lambda):
 * ```kotlin
 * val stubUseCase = PureUseCase<Order, Double> { order -> (order.total * 0.10).asSuccess() }
 * ```
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
 *
 * Example — implementation:
 * ```kotlin
 * class GetUserById(
 *     private val userRepository: UserRepository,
 * ) : SuspendUseCase<UserId, User> {
 *     override suspend fun invoke(params: UserId): DomainResult<User> =
 *         userRepository.findById(params).flatMap { user ->
 *             user?.asSuccess() ?: domainFailure(
 *                 DomainError.NotFound("User", params.value)
 *             )
 *         }
 * }
 * ```
 *
 * Example — call site (ViewModel):
 * ```kotlin
 * viewModelScope.launch {
 *     getUserById(userId)
 *         .onSuccess { user -> _state.value = UserLoaded(user) }
 *         .onFailure { error -> _state.value = UserError(error.message) }
 * }
 * ```
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
 *
 * Example:
 * ```kotlin
 * class ObserveOrders(
 *     private val orderRepository: OrderRepository,
 * ) : FlowUseCase<UserId, List<Order>> {
 *     override fun invoke(params: UserId): Flow<DomainResult<List<Order>>> =
 *         orderRepository.observeByUser(params)
 * }
 *
 * // Call site:
 * observeOrders(userId).collect { result ->
 *     result.onSuccess { orders -> _state.value = OrdersLoaded(orders) }
 * }
 * ```
 */
public fun interface FlowUseCase<in I, out O> {
    public operator fun invoke(params: I): Flow<DomainResult<O>>
}

/**
 * Convenience alias for use cases that take no parameters.
 * Avoids forcing callers to pass [Unit] explicitly.
 *
 * Example:
 * ```kotlin
 * class GetCurrentUser(
 *     private val sessionGateway: SessionGateway,
 * ) : NoParamsUseCase<User> {
 *     override suspend fun invoke(): DomainResult<User> =
 *         sessionGateway.getCurrentUser()
 * }
 *
 * // Call site:
 * val user = getCurrentUser()
 * ```
 */
public fun interface NoParamsUseCase<out O> {
    public suspend operator fun invoke(): DomainResult<O>
}

/**
 * Convenience alias for reactive use cases that take no parameters.
 *
 * Example:
 * ```kotlin
 * class ObserveConnectivity(
 *     private val connectivityGateway: NoParamsFlowGateway<Boolean>,
 * ) : NoParamsFlowUseCase<Boolean> {
 *     override fun invoke(): Flow<DomainResult<Boolean>> =
 *         connectivityGateway.observe()
 * }
 *
 * // Call site:
 * observeConnectivity().collect { result ->
 *     result.onSuccess { isOnline -> updateBanner(isOnline) }
 * }
 * ```
 */
public fun interface NoParamsFlowUseCase<out O> {
    public operator fun invoke(): Flow<DomainResult<O>>
}
