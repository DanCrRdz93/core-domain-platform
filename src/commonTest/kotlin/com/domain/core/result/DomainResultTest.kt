package com.domain.core.result

import com.domain.core.error.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainResultTest {

    @Test
    fun `asSuccess wraps value in Success`() {
        val result = "hello".asSuccess()
        assertIs<DomainResult.Success<String>>(result)
        assertEquals("hello", result.value)
    }

    @Test
    fun `domainFailure wraps error in Failure`() {
        val error = DomainError.Unknown()
        val result = domainFailure(error)
        assertIs<DomainResult.Failure>(result)
        assertEquals(error, result.error)
    }

    @Test
    fun `map transforms success value`() {
        val result = 2.asSuccess().map { it * 3 }
        assertEquals(6, (result as DomainResult.Success).value)
    }

    @Test
    fun `map preserves failure`() {
        val error = DomainError.Validation("field", "is bad")
        val result = domainFailure(error).map { "should not reach" }
        assertEquals(error, (result as DomainResult.Failure).error)
    }

    @Test
    fun `flatMap chains success results`() {
        val result = 5.asSuccess().flatMap { (it * 2).asSuccess() }
        assertEquals(10, (result as DomainResult.Success).value)
    }

    @Test
    fun `flatMap short-circuits on failure`() {
        val error = DomainError.NotFound("Item", "42")
        val result = domainFailure(error).flatMap { "unreachable".asSuccess() }
        assertEquals(error, (result as DomainResult.Failure).error)
    }

    @Test
    fun `getOrNull returns value on success`() {
        assertEquals("value", "value".asSuccess().getOrNull())
    }

    @Test
    fun `getOrNull returns null on failure`() {
        assertNull(domainFailure(DomainError.Unknown()).getOrNull())
    }

    @Test
    fun `getOrElse returns default on failure`() {
        val result = domainFailure(DomainError.Unknown()).getOrElse { "default" }
        assertEquals("default", result)
    }

    @Test
    fun `onSuccess is called for success`() {
        var called = false
        "x".asSuccess().onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun `onFailure is called for failure`() {
        var called = false
        domainFailure(DomainError.Unknown()).onFailure { called = true }
        assertTrue(called)
    }

    @Test
    fun `mapError transforms error`() {
        val original = DomainError.Unknown(detail = "original")
        val mapped = domainFailure(original).mapError { DomainError.Conflict(detail = "mapped") }
        val error = (mapped as DomainResult.Failure).error
        assertIs<DomainError.Conflict>(error)
        assertEquals("mapped", error.message)
    }

    @Test
    fun `runDomainCatching wraps success`() = kotlinx.coroutines.test.runTest {
        val result = runDomainCatching { 42 }
        assertEquals(42, (result as DomainResult.Success).value)
    }

    @Test
    fun `runDomainCatching maps thrown exception to DomainError`() = kotlinx.coroutines.test.runTest {
        val result = runDomainCatching(
            errorMapper = { DomainError.Infrastructure(detail = "infra error", cause = it) }
        ) {
            error("boom")
        }
        val failure = result as DomainResult.Failure
        assertIs<DomainError.Infrastructure>(failure.error)
        assertEquals("infra error", failure.error.message)
    }
}
