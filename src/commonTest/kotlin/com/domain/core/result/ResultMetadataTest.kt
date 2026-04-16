package com.domain.core.result

import com.domain.core.error.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResultMetadataTest {

    @Test
    fun `ResultMetadata EMPTY has all nulls and empty extra`() {
        val meta = ResultMetadata.EMPTY
        assertNull(meta.requestId)
        assertNull(meta.durationMs)
        assertNull(meta.attemptCount)
        assertEquals(emptyMap(), meta.extra)
    }

    @Test
    fun `ResultMetadata carries transport context`() {
        val meta = ResultMetadata(
            requestId = "req-abc-123",
            durationMs = 245L,
            attemptCount = 2,
            extra = mapOf("X-RateLimit-Remaining" to "98"),
        )
        assertEquals("req-abc-123", meta.requestId)
        assertEquals(245L, meta.durationMs)
        assertEquals(2, meta.attemptCount)
        assertEquals("98", meta.extra["X-RateLimit-Remaining"])
    }

    @Test
    fun `DomainResultWithMeta wraps success with metadata`() {
        val wrapped = DomainResultWithMeta(
            result = "hello".asSuccess(),
            metadata = ResultMetadata(requestId = "r-1"),
        )
        assertEquals(true, wrapped.result.isSuccess)
        assertEquals("hello", wrapped.result.getOrNull())
        assertEquals("r-1", wrapped.metadata.requestId)
    }

    @Test
    fun `DomainResultWithMeta wraps failure with default empty metadata`() {
        val wrapped = DomainResultWithMeta<String>(
            result = domainFailure(DomainError.NotFound("User", "42")),
        )
        assertEquals(true, wrapped.result.isFailure)
        assertEquals(ResultMetadata.EMPTY, wrapped.metadata)
    }

    @Test
    fun `DomainResultWithMeta supports destructuring`() {
        val wrapped = DomainResultWithMeta(
            result = 42.asSuccess(),
            metadata = ResultMetadata(durationMs = 100L),
        )
        val (result, meta) = wrapped
        assertEquals(42, result.getOrNull())
        assertEquals(100L, meta.durationMs)
    }
}
