package com.domain.core.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DomainErrorTest {

    @Test
    fun `Validation error carries field and produces message`() {
        val error = DomainError.Validation(field = "email", detail = "must be a valid address")
        assertEquals("email", error.field)
        assertEquals("must be a valid address", error.detail)
        assertEquals("'email' must be a valid address", error.message)
    }

    @Test
    fun `NotFound error generates message from resourceType and id`() {
        val error = DomainError.NotFound(resourceType = "User", id = "abc-123")
        assertEquals("Resource 'User' with id 'abc-123' not found.", error.message)
    }

    @Test
    fun `Infrastructure error wraps cause`() {
        val cause = RuntimeException("db failure")
        val error = DomainError.Infrastructure(detail = "Storage unavailable", cause = cause)
        assertEquals("Storage unavailable", error.message)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `Unknown error defaults to generic message`() {
        val error = DomainError.Unknown()
        assertEquals("An unexpected error occurred.", error.detail)
        assertEquals("An unexpected error occurred.", error.message)
        assertNull(error.cause)
    }

    @Test
    fun `Unauthorized has default message`() {
        val error = DomainError.Unauthorized()
        assertEquals("Unauthorized", error.detail)
        assertEquals("Unauthorized", error.message)
    }

    // ── Data class structural equality ────────────────────────────────────────

    @Test
    fun `Validation equality is structural — same field and detail are equal`() {
        val a = DomainError.Validation("email", "must be valid")
        val b = DomainError.Validation("email", "must be valid")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Validation equality distinguishes different fields`() {
        val a = DomainError.Validation("email", "required")
        val b = DomainError.Validation("phone", "required")
        assertNotEquals(a, b)
    }

    @Test
    fun `Validation equality distinguishes different details`() {
        val a = DomainError.Validation("email", "required")
        val b = DomainError.Validation("email", "must be valid")
        assertNotEquals(a, b)
    }

    @Test
    fun `NotFound equality is structural`() {
        val a = DomainError.NotFound("User", "123")
        val b = DomainError.NotFound("User", "123")
        assertEquals(a, b)
    }

    @Test
    fun `NotFound equality distinguishes different ids`() {
        assertEquals(
            DomainError.NotFound("User", "1"),
            DomainError.NotFound("User", "1"),
        )
        assertNotEquals(
            DomainError.NotFound("User", "1"),
            DomainError.NotFound("User", "2"),
        )
    }

    @Test
    fun `Conflict equality is structural`() {
        val a = DomainError.Conflict(detail = "duplicate")
        val b = DomainError.Conflict(detail = "duplicate")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Unauthorized equality is structural`() {
        assertEquals(DomainError.Unauthorized(), DomainError.Unauthorized())
        assertEquals(
            DomainError.Unauthorized("custom"),
            DomainError.Unauthorized("custom"),
        )
        assertNotEquals(DomainError.Unauthorized(), DomainError.Unauthorized("custom"))
    }

    // ── Subtypes without cause ────────────────────────────────────────────────

    @Test
    fun `Validation has no cause property`() {
        val error = DomainError.Validation("f", "d")
        // cause is not part of Validation — verifying message is synthesised correctly
        assertEquals("'f' d", error.message)
    }

    @Test
    fun `Conflict has no cause property — message is detail`() {
        val error = DomainError.Conflict(detail = "state mismatch")
        assertEquals("state mismatch", error.message)
    }

    @Test
    fun `Infrastructure cause is null when not provided`() {
        val error = DomainError.Infrastructure(detail = "oops")
        assertNull(error.cause)
    }

    @Test
    fun `Unknown cause is null when not provided`() {
        assertNull(DomainError.Unknown().cause)
    }

    // ── Cancelled ────────────────────────────────────────────────────────────

    @Test
    fun `Cancelled has default message`() {
        val error = DomainError.Cancelled()
        assertEquals("Operation was cancelled.", error.message)
        assertEquals("Operation was cancelled.", error.detail)
    }

    @Test
    fun `Cancelled accepts custom detail`() {
        val error = DomainError.Cancelled("User navigated away")
        assertEquals("User navigated away", error.message)
    }

    @Test
    fun `Cancelled equality is structural`() {
        assertEquals(DomainError.Cancelled(), DomainError.Cancelled())
        assertEquals(
            DomainError.Cancelled("custom"),
            DomainError.Cancelled("custom"),
        )
        assertNotEquals(DomainError.Cancelled(), DomainError.Cancelled("custom"))
    }

    // ── Sealed when exhaustiveness ────────────────────────────────────────────

    @Test
    fun `when over DomainError is exhaustive — all branches reachable`() {
        val errors: List<DomainError> = listOf(
            DomainError.Validation("f", "d"),
            DomainError.NotFound("T", "1"),
            DomainError.Unauthorized(),
            DomainError.Conflict(detail = "c"),
            DomainError.Infrastructure(detail = "i"),
            DomainError.Cancelled(),
            DomainError.Unknown(),
        )
        val labels = errors.map { error ->
            when (error) {
                is DomainError.Validation -> "Validation"
                is DomainError.NotFound -> "NotFound"
                is DomainError.Unauthorized -> "Unauthorized"
                is DomainError.Conflict -> "Conflict"
                is DomainError.Infrastructure -> "Infrastructure"
                is DomainError.Cancelled -> "Cancelled"
                is DomainError.Unknown -> "Unknown"
            }
        }
        assertEquals(
            listOf("Validation", "NotFound", "Unauthorized", "Conflict", "Infrastructure", "Cancelled", "Unknown"),
            labels,
        )
    }
}
