package com.domain.core.validation

import com.domain.core.error.DomainError
import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure
import com.domain.core.testing.shouldFailWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidatorTest {

    @Test
    fun `notBlankValidator succeeds for non-blank string`() {
        val result = notBlankValidator("name").validate("Alice")
        assertIs<DomainResult.Success<Unit>>(result)
    }

    @Test
    fun `notBlankValidator fails for blank string`() {
        val result = notBlankValidator("name").validate("   ")
        val failure = result as DomainResult.Failure
        assertIs<DomainError.Validation>(failure.error)
    }

    @Test
    fun `maxLengthValidator succeeds within limit`() {
        val result = maxLengthValidator("bio", 10).validate("hello")
        assertIs<DomainResult.Success<Unit>>(result)
    }

    @Test
    fun `maxLengthValidator fails when exceeds limit`() {
        val result = maxLengthValidator("bio", 3).validate("toolong")
        assertIs<DomainResult.Failure>(result)
    }

    @Test
    fun `minLengthValidator succeeds when meets minimum`() {
        val result = minLengthValidator("code", 4).validate("1234")
        assertIs<DomainResult.Success<Unit>>(result)
    }

    @Test
    fun `minLengthValidator fails when below minimum`() {
        val result = minLengthValidator("code", 6).validate("abc")
        assertIs<DomainResult.Failure>(result)
    }

    @Test
    fun `rangeValidator succeeds within range`() {
        val result = rangeValidator("age", 0, 120).validate(30)
        assertIs<DomainResult.Success<Unit>>(result)
    }

    @Test
    fun `rangeValidator fails outside range`() {
        val result = rangeValidator("age", 0, 120).validate(150)
        assertIs<DomainResult.Failure>(result)
    }

    @Test
    fun `andThen runs second validator when first passes`() {
        val combined = notBlankValidator("field").andThen(maxLengthValidator("field", 5))
        assertIs<DomainResult.Failure>(combined.validate("toolong"))
        assertIs<DomainResult.Success<Unit>>(combined.validate("ok"))
    }

    @Test
    fun `andThen short-circuits on first failure`() {
        var secondCalled = false
        val second = Validator<String> { _ ->
            secondCalled = true
            Unit.asSuccess()
        }
        val combined = notBlankValidator("field").andThen(second)
        combined.validate("  ")
        assertTrue(!secondCalled)
    }

    // ── Boundary values ───────────────────────────────────────────────────────

    @Test
    fun `maxLengthValidator passes at exact limit`() {
        assertIs<DomainResult.Success<Unit>>(maxLengthValidator("f", 5).validate("hello"))
    }

    @Test
    fun `maxLengthValidator fails at limit + 1`() {
        assertIs<DomainResult.Failure>(maxLengthValidator("f", 5).validate("hello!"))
    }

    @Test
    fun `minLengthValidator passes at exact minimum`() {
        assertIs<DomainResult.Success<Unit>>(minLengthValidator("f", 3).validate("abc"))
    }

    @Test
    fun `minLengthValidator fails at minimum - 1`() {
        assertIs<DomainResult.Failure>(minLengthValidator("f", 3).validate("ab"))
    }

    @Test
    fun `rangeValidator passes at exact min boundary`() {
        assertIs<DomainResult.Success<Unit>>(rangeValidator("n", 1, 10).validate(1))
    }

    @Test
    fun `rangeValidator passes at exact max boundary`() {
        assertIs<DomainResult.Success<Unit>>(rangeValidator("n", 1, 10).validate(10))
    }

    @Test
    fun `rangeValidator fails one below min`() {
        assertIs<DomainResult.Failure>(rangeValidator("n", 1, 10).validate(0))
    }

    @Test
    fun `rangeValidator fails one above max`() {
        assertIs<DomainResult.Failure>(rangeValidator("n", 1, 10).validate(11))
    }

    // ── Field name propagation ────────────────────────────────────────────────

    @Test
    fun `notBlankValidator carries field name in error`() {
        val error = notBlankValidator("username").validate("  ").shouldFailWith<DomainError.Validation>()
        assertEquals("username", error.field)
    }

    @Test
    fun `maxLengthValidator carries field name in error`() {
        val error = maxLengthValidator("bio", 3).validate("toolong").shouldFailWith<DomainError.Validation>()
        assertEquals("bio", error.field)
    }

    @Test
    fun `minLengthValidator carries field name in error`() {
        val error = minLengthValidator("pin", 4).validate("ab").shouldFailWith<DomainError.Validation>()
        assertEquals("pin", error.field)
    }

    @Test
    fun `rangeValidator carries field name in error`() {
        val error = rangeValidator("score", 0, 100).validate(150).shouldFailWith<DomainError.Validation>()
        assertEquals("score", error.field)
    }

    // ── Custom validator via fun interface ────────────────────────────────────

    @Test
    fun `custom Validator lambda is invoked`() {
        var invoked = false
        val custom = Validator<Int> { invoked = true; Unit.asSuccess() }
        custom.validate(42)
        assertTrue(invoked)
    }

    @Test
    fun `custom Validator can return Validation failure`() {
        val custom = Validator<Int> { v ->
            if (v > 0) Unit.asSuccess()
            else domainFailure(DomainError.Validation("n", "must be positive"))
        }
        assertIs<DomainResult.Failure>(custom.validate(-1))
        assertIs<DomainResult.Success<Unit>>(custom.validate(1))
    }
}
