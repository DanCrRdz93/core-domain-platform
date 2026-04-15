package com.domain.core.provider

/**
 * Domain abstraction over unique identifier generation.
 *
 * Design rationale:
 * - ID generation is a side effect (produces a new value each call).
 *   Hiding it behind a `fun interface` makes every use case that creates
 *   aggregates fully deterministic and trivially testable.
 * - Returns [String] by default: UUIDs, ULIDs, NanoIDs, and sequential IDs
 *   all reduce to strings at the domain boundary. The concrete [EntityId]
 *   value class wraps this string with type safety.
 * - No dependency on `java.util.UUID` or any platform API inside the domain.
 *   The platform-specific UUID/ULID generation lives in the data/infra layer.
 *
 * Production wiring (data/app layer, NOT here):
 * ```kotlin
 * // JVM / Android:
 * val idProvider: IdProvider = IdProvider { java.util.UUID.randomUUID().toString() }
 * // iOS (via kotlinx or platform interop):
 * val idProvider: IdProvider = IdProvider { platform.Foundation.NSUUID().UUIDString }
 * // Any platform — sequential for deterministic ordering:
 * var counter = 0L
 * val seqProvider: IdProvider = IdProvider { "${++counter}" }
 * ```
 *
 * Test wiring (zero dependencies, fully deterministic):
 * ```kotlin
 * var counter = 0
 * val testIds: IdProvider = IdProvider { "id-${++counter}" }
 * ```
 */
public fun interface IdProvider {
    public fun next(): String
}
