package com.domain.core.result

import com.domain.core.error.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DomainResultZipTest {

    private val err = DomainError.Unknown("e")

    @Test
    fun `zip two successes applies transform`() {
        val result = 2.asSuccess().zip(3.asSuccess()) { a, b -> a + b }
        assertEquals(5, (result as DomainResult.Success).value)
    }

    @Test
    fun `zip left failure returns left error`() {
        val result = domainFailure(err).zip(3.asSuccess()) { a: Int, b: Int -> a + b }
        assertIs<DomainResult.Failure>(result)
        assertEquals(err, result.error)
    }

    @Test
    fun `zip right failure returns right error`() {
        val result = 2.asSuccess().zip(domainFailure(err)) { a, b: Int -> a + b }
        assertIs<DomainResult.Failure>(result)
        assertEquals(err, result.error)
    }

    @Test
    fun `zip three successes applies transform`() {
        val result = 1.asSuccess().zip(2.asSuccess(), 3.asSuccess()) { a, b, c -> a + b + c }
        assertEquals(6, (result as DomainResult.Success).value)
    }

    @Test
    fun `zip three - first failure short-circuits`() {
        val result = domainFailure(err).zip(2.asSuccess(), 3.asSuccess()) { a: Int, b: Int, c: Int -> a + b + c }
        assertIs<DomainResult.Failure>(result)
        assertEquals(err, result.error)
    }
}
