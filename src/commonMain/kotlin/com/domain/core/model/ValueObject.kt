package com.domain.core.model

/**
 * Marker interface for domain value objects — objects defined entirely by
 * their attributes, with no identity of their own.
 *
 * Design rationale:
 * - Value objects must be immutable. Equality is structural (all fields).
 *   Kotlin `data class` satisfies this automatically.
 * - Value objects should self-validate upon construction. Use factory functions
 *   or `init` blocks that return/throw domain-appropriate errors.
 * - This marker has no methods: it is a semantic tag, not a behavioural contract.
 *   Adding behaviour here would couple all value objects to unrelated concerns.
 * - Value objects should be small and focused. Do not create value objects for
 *   every primitive — only when the type carries business rules or prevents
 *   primitive obsession at meaningful boundaries.
 */
public interface ValueObject
