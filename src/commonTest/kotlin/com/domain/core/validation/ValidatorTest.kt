package com.domain.core.validation

import com.domain.core.error.DomainError
import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import kotlin.test.Test
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
}
