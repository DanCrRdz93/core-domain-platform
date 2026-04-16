package com.domain.core.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class PaginationTest {

    @Test
    fun `Page hasNext is true when totalPages is known and more pages exist`() {
        val page = Page(items = listOf("a", "b"), page = 0, size = 2, totalPages = 3)
        assertTrue(page.hasNext)
    }

    @Test
    fun `Page hasNext is false on last page`() {
        val page = Page(items = listOf("a"), page = 2, size = 2, totalPages = 3)
        assertFalse(page.hasNext)
    }

    @Test
    fun `Page hasNext falls back to items size equals page size when totalPages is null`() {
        val full = Page(items = listOf("a", "b"), page = 0, size = 2)
        assertTrue(full.hasNext)

        val partial = Page(items = listOf("a"), page = 0, size = 2)
        assertFalse(partial.hasNext)
    }

    @Test
    fun `Page hasPrevious is false on first page`() {
        val page = Page(items = listOf("a"), page = 0, size = 10)
        assertFalse(page.hasPrevious)
    }

    @Test
    fun `Page hasPrevious is true on second page`() {
        val page = Page(items = listOf("a"), page = 1, size = 10)
        assertTrue(page.hasPrevious)
    }

    @Test
    fun `Page isEmpty reflects items`() {
        val empty = Page<String>(items = emptyList(), page = 0, size = 10)
        assertTrue(empty.isEmpty)

        val nonEmpty = Page(items = listOf("a"), page = 0, size = 10)
        assertFalse(nonEmpty.isEmpty)
    }

    @Test
    fun `PageRequest has sensible defaults`() {
        val request = PageRequest()
        assertEquals(0, request.page)
        assertEquals(20, request.size)
    }

    @Test
    fun `PageRequest rejects negative page`() {
        assertFailsWith<IllegalArgumentException> { PageRequest(page = -1) }
    }

    @Test
    fun `PageRequest rejects size zero`() {
        assertFailsWith<IllegalArgumentException> { PageRequest(size = 0) }
    }

    @Test
    fun `PageRequest rejects size over 100`() {
        assertFailsWith<IllegalArgumentException> { PageRequest(size = 101) }
    }
}
