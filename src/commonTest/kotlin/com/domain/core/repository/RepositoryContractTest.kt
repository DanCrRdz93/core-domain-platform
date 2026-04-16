package com.domain.core.repository

import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.testing.shouldBeSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryContractTest {

    private data class Widget(val id: String, val name: String)

    // ── existsById default implementation ──────────────────────────────────────

    private class FakeReadRepo(private val store: Map<String, Widget>) : ReadRepository<String, Widget> {
        override suspend fun findById(id: String): DomainResult<Widget?> =
            store[id].asSuccess()
    }

    @Test
    fun `existsById returns true when entity exists`() = runTest {
        val repo = FakeReadRepo(mapOf("1" to Widget("1", "Bolt")))
        val result = repo.existsById("1").shouldBeSuccess()
        assertTrue(result)
    }

    @Test
    fun `existsById returns false when entity does not exist`() = runTest {
        val repo = FakeReadRepo(emptyMap())
        val result = repo.existsById("999").shouldBeSuccess()
        assertFalse(result)
    }

    // ── ReadWriteRepository compiles and is implementable ──────────────────────

    private class InMemoryWidgetRepo : ReadWriteRepository<String, Widget> {
        private val store = mutableMapOf<String, Widget>()

        override suspend fun findById(id: String) = store[id].asSuccess()
        override suspend fun findAll() = store.values.toList().asSuccess()
        override fun observeAll(): Flow<DomainResult<List<Widget>>> = flowOf(store.values.toList().asSuccess())
        override suspend fun save(entity: Widget): DomainResult<Unit> {
            store[entity.id] = entity
            return Unit.asSuccess()
        }
        override suspend fun delete(entity: Widget): DomainResult<Unit> {
            store.remove(entity.id)
            return Unit.asSuccess()
        }
    }

    @Test
    fun `ReadWriteRepository save and findById round-trip`() = runTest {
        val repo = InMemoryWidgetRepo()
        repo.save(Widget("1", "Gear"))
        val found = repo.findById("1").shouldBeSuccess()
        assertEquals("Gear", found?.name)
    }

    @Test
    fun `ReadWriteRepository findAll returns all saved entities`() = runTest {
        val repo = InMemoryWidgetRepo()
        repo.save(Widget("1", "Gear"))
        repo.save(Widget("2", "Bolt"))
        val all = repo.findAll().shouldBeSuccess()
        assertEquals(2, all.size)
    }

    @Test
    fun `ReadWriteRepository delete removes entity`() = runTest {
        val repo = InMemoryWidgetRepo()
        val widget = Widget("1", "Gear")
        repo.save(widget)
        repo.delete(widget)
        val found = repo.findById("1").shouldBeSuccess()
        assertEquals(null, found)
    }
}
