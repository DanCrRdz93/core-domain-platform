package com.domain.core.provider

/**
 * Domain abstraction over the system clock.
 *
 * Design rationale:
 * - Any use case that needs "now" (`createdAt`, `expiresAt`, `scheduledFor`)
 *   must receive time as a dependency, not read it from a global.
 *   `kotlinx.datetime.Clock.System.now()` is a hidden side effect that makes
 *   tests time-dependent and non-deterministic.
 * - [ClockProvider] is a `fun interface`: in production, inject a one-liner
 *   implementation; in tests, inject a fixed instant — zero mocking framework needed.
 * - Returns [Long] epoch millis rather than a platform type:
 *   • No `kotlinx-datetime` dependency forced on the domain.
 *   • Consumers that need richer types (Instant, LocalDate) convert at their boundary.
 *   • Long is a primitive — no allocation on JVM/ART.
 *
 * Production wiring example (data/app layer, NOT here):
 * ```kotlin
 * val clock: ClockProvider = ClockProvider { System.currentTimeMillis() }
 * ```
 *
 * Test wiring example:
 * ```kotlin
 * val fixedClock: ClockProvider = ClockProvider { 1_700_000_000_000L }
 * ```
 */
public fun interface ClockProvider {
    public fun nowMillis(): Long
}
