package com.domain.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdProviderTest {

    @Test
    fun `IdProvider returns configured value`() {
        val fixed: IdProvider = IdProvider { "fixed-id" }
        assertEquals("fixed-id", fixed.next())
    }

    @Test
    fun `Sequential IdProvider produces distinct values`() {
        var counter = 0
        val sequential: IdProvider = IdProvider { "id-${++counter}" }
        val first = sequential.next()
        val second = sequential.next()
        assertNotEquals(first, second)
        assertEquals("id-1", first)
        assertEquals("id-2", second)
    }
}
