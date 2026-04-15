package com.domain.core.model

/**
 * Marker interface for domain entities — objects with identity.
 *
 * Design rationale:
 * - Entities are distinguished by identity, not by attribute values.
 *   Equality must be based on [id], not on the whole state.
 * - [ID] is a generic parameter to support typed identities (String, Long,
 *   custom value objects) without casting.
 * - This interface does NOT enforce equals/hashCode because Kotlin `data class`
 *   covers that automatically when [id] is the only constructor property used
 *   for comparison. Implementors are expected to be `data class` or otherwise
 *   explicitly override equality based on [id].
 * - No mutable state. Domain entities must be immutable; mutations produce
 *   new instances via `copy()`.
 */
public interface Entity<out ID> {
    public val id: ID
}
