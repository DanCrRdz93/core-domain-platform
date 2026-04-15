package com.domain.core.error

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
