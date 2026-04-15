package com.domain.core.result

import com.domain.core.error.DomainError
import com.domain.core.testing.shouldBeFailure
import com.domain.core.testing.shouldBeSuccess
import com.domain.core.testing.shouldFailWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
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

    // ── isSuccess / isFailure flags ───────────────────────────────────────────

    @Test
    fun `isSuccess is true for Success`() {
        assertTrue("x".asSuccess().isSuccess)
    }

    @Test
    fun `isSuccess is false for Failure`() {
        assertTrue(!domainFailure(DomainError.Unknown()).isSuccess)
    }

    @Test
    fun `isFailure is true for Failure`() {
        assertTrue(domainFailure(DomainError.Unknown()).isFailure)
    }

    @Test
    fun `isFailure is false for Success`() {
        assertTrue(!"x".asSuccess().isFailure)
    }

    // ── onSuccess / onFailure return this (chain) ─────────────────────────────

    @Test
    fun `onSuccess returns the same instance for chaining`() {
        val original = "x".asSuccess()
        val returned = original.onSuccess { }
        assertSame(original, returned)
    }

    @Test
    fun `onFailure returns the same instance for chaining`() {
        val original = domainFailure(DomainError.Unknown())
        val returned = original.onFailure { }
        assertSame(original, returned)
    }

    @Test
    fun `onSuccess is NOT called for failure`() {
        var called = false
        domainFailure(DomainError.Unknown()).onSuccess { called = true }
        assertTrue(!called)
    }

    @Test
    fun `onFailure is NOT called for success`() {
        var called = false
        "x".asSuccess().onFailure { called = true }
        assertTrue(!called)
    }

    @Test
    fun `onSuccess and onFailure can be chained on success`() {
        var successCalled = false
        var failureCalled = false
        "x".asSuccess()
            .onSuccess { successCalled = true }
            .onFailure { failureCalled = true }
        assertTrue(successCalled)
        assertTrue(!failureCalled)
    }

    // ── mapError is no-op on Success ──────────────────────────────────────────

    @Test
    fun `mapError does not transform success`() {
        val result = "x".asSuccess().mapError { DomainError.Conflict(detail = "should not run") }
        assertEquals("x", result.shouldBeSuccess())
    }

    // ── getOrElse receives the error ──────────────────────────────────────────

    @Test
    fun `getOrElse lambda receives the actual DomainError`() {
        val error = DomainError.Conflict(detail = "state conflict")
        var received: DomainError? = null
        domainFailure(error).getOrElse { received = it; "fallback" }
        assertEquals(error, received)
    }

    @Test
    fun `getOrElse does not call lambda on success`() {
        var called = false
        "x".asSuccess().getOrElse { called = true; "fallback" }
        assertTrue(!called)
    }

    // ── flatMap identity laws ─────────────────────────────────────────────────

    @Test
    fun `flatMap on failure does not invoke transform`() {
        var called = false
        val error = DomainError.Unknown()
        val result = domainFailure(error).flatMap<String, String> { called = true; "unreachable".asSuccess() }
        assertTrue(!called)
        assertEquals(error, result.shouldBeFailure())
    }

    // ── runDomainCatching default error mapper ────────────────────────────────

    @Test
    fun `runDomainCatching uses Unknown as default error mapper`() = kotlinx.coroutines.test.runTest {
        val result = runDomainCatching { error("boom") }
        val error = result.shouldFailWith<DomainError.Unknown>()
        assertTrue(error.cause is IllegalStateException)
    }

    // ── TestDoubles assertion helpers ─────────────────────────────────────────

    @Test
    fun `shouldBeSuccess extracts value`() {
        assertEquals(42, 42.asSuccess().shouldBeSuccess())
    }

    @Test
    fun `shouldBeFailure extracts error`() {
        val error = DomainError.Unknown()
        assertEquals(error, domainFailure(error).shouldBeFailure())
    }

    @Test
    fun `shouldFailWith casts to expected error subtype`() {
        val error = DomainError.NotFound("User", "99")
        val extracted = domainFailure(error).shouldFailWith<DomainError.NotFound>()
        assertEquals("User", extracted.resourceType)
        assertEquals("99", extracted.id)
    }
}
