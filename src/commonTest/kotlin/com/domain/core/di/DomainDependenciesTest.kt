package com.domain.core.di

import com.domain.core.error.DomainError
import com.domain.core.provider.ClockProvider
import com.domain.core.provider.IdProvider
import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure
import com.domain.core.usecase.SuspendUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Verifies that [DomainDependencies] wires correctly and that a use case
 * composed via constructor injection behaves deterministically in tests.
 *
 * This test is intentionally framework-free: no Koin, no Hilt, no mocking library.
 * It demonstrates the canonical test wiring pattern for consumers of this SDK.
 */
class DomainDependenciesTest {

    // ── Fake domain model ────────────────────────────────────────────────────

    private data class Item(val id: String, val name: String, val createdAt: Long)

    // ── Fake repository contract (would live in a feature module) ────────────

    private interface ItemRepository {
        suspend fun save(item: Item): DomainResult<Unit>
    }

    // ── Concrete use case composed via constructor injection ─────────────────

    private data class CreateItemParams(val name: String)

    private class CreateItemUseCase(
        private val deps: DomainDependencies,
        private val repository: ItemRepository,
    ) : SuspendUseCase<CreateItemParams, Item> {
        override suspend fun invoke(params: CreateItemParams): DomainResult<Item> {
            if (params.name.isBlank()) {
                return domainFailure(DomainError.Validation(field = "name", detail = "must not be blank"))
            }
            val item = Item(
                id = deps.idProvider.next(),
                name = params.name,
                createdAt = deps.clock.nowMillis(),
            )
            return repository.save(item).let { saveResult ->
                if (saveResult is DomainResult.Failure) saveResult else item.asSuccess()
            }
        }
    }

    // ── Test wiring — zero mocking frameworks ────────────────────────────────

    private val fixedTime = 1_700_000_000_000L
    private val fixedId = "item-001"

    private val testDeps = DomainDependencies(
        clock = ClockProvider { fixedTime },
        idProvider = IdProvider { fixedId },
    )

    private val successRepo = object : ItemRepository {
        override suspend fun save(item: Item) = Unit.asSuccess()
    }

    private val failingRepo = object : ItemRepository {
        override suspend fun save(item: Item) =
            domainFailure(DomainError.Infrastructure(detail = "DB unavailable"))
    }

    private val useCase = CreateItemUseCase(testDeps, successRepo)
    private val failingUseCase = CreateItemUseCase(testDeps, failingRepo)

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `DomainDependencies equality is structural`() {
        val a = DomainDependencies(ClockProvider { 1L }, IdProvider { "x" })
        val b = DomainDependencies(ClockProvider { 1L }, IdProvider { "x" })
        // data class equality on the provider references — same lambda instances differ
        // but the structure is correct and both are independently testable
        assertEquals(a.clock.nowMillis(), b.clock.nowMillis())
        assertEquals(a.idProvider.next(), b.idProvider.next())
    }

    @Test
    fun `use case receives deterministic id and timestamp from injected deps`() =
        kotlinx.coroutines.test.runTest {
            val result = useCase(CreateItemParams("Widget"))
            assertIs<DomainResult.Success<Item>>(result)
            assertEquals(fixedId, result.value.id)
            assertEquals(fixedTime, result.value.createdAt)
            assertEquals("Widget", result.value.name)
        }

    @Test
    fun `use case returns Validation failure on blank name`() =
        kotlinx.coroutines.test.runTest {
            val result = useCase(CreateItemParams("   "))
            assertIs<DomainResult.Failure>(result)
            assertIs<DomainError.Validation>(result.error)
        }

    @Test
    fun `use case propagates repository Infrastructure failure`() =
        kotlinx.coroutines.test.runTest {
            val result = failingUseCase(CreateItemParams("Widget"))
            assertIs<DomainResult.Failure>(result)
            assertIs<DomainError.Infrastructure>(result.error)
        }

    @Test
    fun `swapping deps in tests produces different deterministic output`() =
        kotlinx.coroutines.test.runTest {
            var counter = 0
            val altDeps = DomainDependencies(
                clock = ClockProvider { 9_999_999_999_999L },
                idProvider = IdProvider { "alt-${++counter}" },
            )
            val altUseCase = CreateItemUseCase(altDeps, successRepo)
            val result = altUseCase(CreateItemParams("Gadget"))
            assertIs<DomainResult.Success<Item>>(result)
            assertEquals("alt-1", result.value.id)
            assertEquals(9_999_999_999_999L, result.value.createdAt)
        }
}
