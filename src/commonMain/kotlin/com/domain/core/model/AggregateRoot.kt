package com.domain.core.model

/**
 * Marker interface for aggregate roots — the entry point through which all
 * interactions with an aggregate must pass.
 *
 * Design rationale:
 * - An aggregate root is an [Entity] that also acts as the consistency boundary
 *   for a cluster of related objects.
 * - Repository contracts are typed against [AggregateRoot] subtypes, not raw
 *   entities, enforcing that repositories always operate at the aggregate boundary.
 * - No methods are declared here. Cross-cutting behaviour (e.g., domain events)
 *   will be added in a later phase when the event model is designed, to avoid
 *   premature abstraction.
 */
public interface AggregateRoot<out ID> : Entity<ID>
