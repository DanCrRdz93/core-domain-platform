package com.domain.core.di

import com.domain.core.provider.ClockProvider
import com.domain.core.provider.IdProvider

/**
 * Immutable container for cross-cutting infrastructure dependencies that the
 * domain layer requires but must not implement.
 *
 * Design rationale:
 * ─ Constructor injection, zero magic.
 *   This is a plain data class. The app/data layer builds one instance and
 *   passes it explicitly to every [DomainModule] implementation. No service
 *   locator, no global object, no reflection.
 *
 * ─ Why group these two providers?
 *   [ClockProvider] and [IdProvider] are the only side-effectful primitives
 *   the domain needs universally: time and identity generation. Every aggregate
 *   creation and every timestamped operation depends on them.
 *   Passing them individually to every use case constructor creates N×2 noise.
 *   Grouping them in a single value eliminates the noise without introducing
 *   abstraction cost: the type is concrete, sealed, and its fields are typed
 *   interfaces, not `Any`.
 *
 * ─ No business-feature repositories or gateways here.
 *   Those are specific to each feature and are injected directly into the
 *   concrete use case constructors that need them. Only truly cross-cutting
 *   infrastructure belongs here.
 *
 * ─ Testability.
 *   In tests: `DomainDependencies(clock = { FIXED_MS }, idProvider = { "id-1" })`.
 *   Zero mocking frameworks required. One line.
 *
 * ─ Immutability.
 *   `data class` with `val` fields only. No mutation after construction.
 *   Concurrency-safe — can be shared across coroutines and threads freely.
 *
 * App-layer wiring example (NOT in this module):
 * ```kotlin
 * val domainDeps = DomainDependencies(
 *     clock = ClockProvider { System.currentTimeMillis() },
 *     idProvider = IdProvider { UUID.randomUUID().toString() },
 * )
 * ```
 *
 * Test wiring example:
 * ```kotlin
 * val testDeps = DomainDependencies(
 *     clock = ClockProvider { 1_700_000_000_000L },
 *     idProvider = IdProvider { "fixed-id" },
 * )
 * ```
 */
public data class DomainDependencies(
    val clock: ClockProvider,
    val idProvider: IdProvider,
)
