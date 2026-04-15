package com.domain.core.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DomainErrorTest {

    @Test
    fun `Validation error carries field and message`() {
        val error = DomainError.Validation(field = "email", message = "Invalid email")
        assertEquals("email", error.field)
        assertEquals("Invalid email", error.message)
    }

    @Test
    fun `NotFound error generates message from resourceType and id`() {
        val error = DomainError.NotFound(resourceType = "User", id = "abc-123")
        assertEquals("Resource 'User' with id 'abc-123' not found.", error.message)
    }

    @Test
    fun `Infrastructure error wraps cause`() {
        val cause = RuntimeException("db failure")
        val error = DomainError.Infrastructure(message = "Storage unavailable", cause = cause)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `Unknown error defaults to generic message`() {
        val error = DomainError.Unknown()
        assertEquals("An unexpected error occurred.", error.message)
        assertNull(error.cause)
    }

    @Test
    fun `Unauthorized has default message`() {
        val error = DomainError.Unauthorized()
        assertEquals("Unauthorized", error.message)
    }
}
