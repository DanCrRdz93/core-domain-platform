package com.domain.core.validation

import com.domain.core.result.DomainResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidateAllTest {

    private val notBlank = notBlankValidator("field")
    private val maxFive = maxLengthValidator("field", 5)
    private val minTwo = minLengthValidator("field", 2)

    @Test
    fun `validateAll returns Success when all validators pass`() {
        val result = validateAll("abc", listOf(notBlank, maxFive, minTwo))
        assertIs<DomainResult.Success<Unit>>(result)
    }

    @Test
    fun `validateAll returns Failure on first error when one validator fails`() {
        val result = validateAll("", listOf(notBlank, maxFive))
        assertIs<DomainResult.Failure>(result)
    }

    @Test
    fun `validateAll with empty validator list always succeeds`() {
        val result = validateAll("anything", emptyList())
        assertIs<DomainResult.Success<Unit>>(result)
    }

    @Test
    fun `collectValidationErrors returns empty list when all pass`() {
        val errors = collectValidationErrors("abc", listOf(notBlank, maxFive, minTwo))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `collectValidationErrors collects all failures`() {
        val errors = collectValidationErrors("x".repeat(10), listOf(notBlank, maxFive, minTwo))
        assertEquals(1, errors.size)
    }

    @Test
    fun `collectValidationErrors reports multiple errors`() {
        val tooLongAndNotBlank = collectValidationErrors(
            value = "x".repeat(10),
            validators = listOf(maxFive, maxLengthValidator("field", 3)),
        )
        assertEquals(2, tooLongAndNotBlank.size)
    }
}
