package com.domain.core.event

/**
 * Marker interface for domain events — things that happened in the domain
 * that other parts of the system might be interested in.
 *
 * Design rationale:
 * - Domain events decouple the producer (use case / aggregate) from consumers
 *   (other use cases, analytics, notifications, projections).
 * - [occurredAt] is mandatory: events are facts that happened at a point in time.
 *   Use [com.domain.core.provider.ClockProvider] to generate this value so that
 *   tests remain deterministic.
 * - Events are data-only (no behaviour). They cross module boundaries as
 *   serialisable values, not active objects.
 * - This is intentionally minimal: no event bus, no dispatcher. The SDK provides
 *   the contract; the app layer decides how to dispatch (in-process, message queue, etc.).
 *
 * Usage:
 * ```kotlin
 * data class OrderPlaced(
 *     val orderId: OrderId,
 *     val total: Double,
 *     override val occurredAt: Long,
 * ) : DomainEvent
 * ```
 */
public interface DomainEvent {
    public val occurredAt: Long
}

/**
 * Contract for publishing domain events.
 *
 * Design rationale:
 * - Use cases call [publish] after a successful operation.
 * - The implementation decides delivery semantics: synchronous in-process,
 *   async via Channel/SharedFlow, or external message broker.
 * - Returns nothing (fire-and-forget from the domain's perspective).
 *   If delivery guarantees are needed, the infrastructure implementation
 *   handles persistence/retry internally.
 */
public fun interface DomainEventPublisher {
    public suspend fun publish(event: DomainEvent)
}

/**
 * Contract for subscribing to domain events.
 *
 * Design rationale:
 * - Consumers (projections, notification triggers, analytics) implement
 *   [DomainEventHandler] and are registered with the dispatcher.
 * - [T] narrows the event type so handlers are type-safe.
 */
public fun interface DomainEventHandler<in T : DomainEvent> {
    public suspend fun handle(event: T)
}
