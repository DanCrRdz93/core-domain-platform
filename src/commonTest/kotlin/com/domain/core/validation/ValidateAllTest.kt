package com.domain.core.validation

import com.domain.core.error.DomainError
import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure
import com.domain.core.testing.shouldFailWith
import com.domain.core.testing.validationError
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

    // ── validateAll evaluates ALL validators (no short-circuit) ───────────────

    @Test
    fun `validateAll evaluates all validators even after first failure`() {
        val evaluationOrder = mutableListOf<Int>()
        val v1 = Validator<String> { evaluationOrder += 1; notBlank.validate(it) }
        val v2 = Validator<String> { evaluationOrder += 2; maxFive.validate(it) }
        val v3 = Validator<String> { evaluationOrder += 3; minTwo.validate(it) }

        validateAll("", listOf(v1, v2, v3))

        assertEquals(listOf(1, 2, 3), evaluationOrder)
    }

    @Test
    fun `validateAll returns first encountered error`() {
        val firstError = validationError("first", "bad")
        val secondError = validationError("second", "also bad")
        val v1 = Validator<String> { domainFailure(firstError) }
        val v2 = Validator<String> { domainFailure(secondError) }

        val result = validateAll("x", listOf(v1, v2))
        val error = result.shouldFailWith<DomainError.Validation>()
        assertEquals("first", error.field)
    }

    // ── collectValidationErrors preserves declaration order ───────────────────

    @Test
    fun `collectValidationErrors preserves declaration order`() {
        val e1 = validationError("a", "first")
        val e2 = validationError("b", "second")
        val e3 = validationError("c", "third")
        val validators = listOf(
            Validator<String> { domainFailure(e1) },
            Validator<String> { domainFailure(e2) },
            Validator<String> { domainFailure(e3) },
        )

        val errors = collectValidationErrors("x", validators)

        assertEquals(3, errors.size)
        assertEquals(e1, errors[0])
        assertEquals(e2, errors[1])
        assertEquals(e3, errors[2])
    }

    @Test
    fun `collectValidationErrors only collects failures — not successes`() {
        val e = validationError("f", "bad")
        val validators = listOf(
            Validator<String> { Unit.asSuccess() },
            Validator<String> { domainFailure(e) },
            Validator<String> { Unit.asSuccess() },
        )
        val errors = collectValidationErrors("x", validators)
        assertEquals(1, errors.size)
        assertEquals(e, errors[0])
    }
}
