package com.domain.core.model

/**
 * Typed identity wrapper for domain entities.
 *
 * Design rationale:
 * - Prevents primitive obsession: passing a raw [String] or [Long] as an ID
 *   has no semantic meaning and allows any string to be used as any ID type.
 * - Each aggregate defines its own concrete ID by implementing this interface,
 *   making IDs incompatible across aggregate boundaries at the type system level.
 * - [T] is the underlying primitive type (String, Long, UUID-as-String, etc.).
 * - Equality is structural and delegated to [value], which data class implementors
 *   get for free.
 *
 * Usage:
 * ```kotlin
 * @JvmInline
 * value class UserId(override val value: String) : EntityId<String>
 * ```
 *
 * Using `@JvmInline value class` eliminates boxing on JVM/ART for primitive-backed IDs.
 */
public interface EntityId<out T> : ValueObject {
    public val value: T
}
