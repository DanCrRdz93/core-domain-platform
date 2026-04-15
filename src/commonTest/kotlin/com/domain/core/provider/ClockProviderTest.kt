package com.domain.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClockProviderTest {

    @Test
    fun `ClockProvider returns configured fixed value`() {
        val fixed: ClockProvider = ClockProvider { 1_700_000_000_000L }
        assertEquals(1_700_000_000_000L, fixed.nowMillis())
    }

    @Test
    fun `ClockProvider lambda is invoked on each call`() {
        var callCount = 0
        val counting: ClockProvider = ClockProvider { callCount++; callCount.toLong() }
        counting.nowMillis()
        counting.nowMillis()
        assertEquals(2, callCount)
    }

    @Test
    fun `Two different ClockProvider instances are independent`() {
        val a: ClockProvider = ClockProvider { 1L }
        val b: ClockProvider = ClockProvider { 2L }
        assertTrue(a.nowMillis() != b.nowMillis())
    }
}
