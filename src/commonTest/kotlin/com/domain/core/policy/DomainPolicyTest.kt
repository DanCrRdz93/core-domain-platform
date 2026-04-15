package com.domain.core.policy

import com.domain.core.error.DomainError
import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure
import com.domain.core.testing.shouldFailWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DomainPolicyTest {

    private val alwaysSatisfied: DomainPolicy<Int> = DomainPolicy { Unit.asSuccess() }
    private val alwaysViolated: DomainPolicy<Int> = DomainPolicy {
        domainFailure(DomainError.Conflict(detail = "violated"))
    }

    @Test
    fun `satisfied policy returns Success`() {
        assertIs<DomainResult.Success<Unit>>(alwaysSatisfied.evaluate(1))
    }

    @Test
    fun `violated policy returns Failure`() {
        assertIs<DomainResult.Failure>(alwaysViolated.evaluate(1))
    }

    @Test
    fun `and - both satisfied returns Success`() {
        val combined = alwaysSatisfied and alwaysSatisfied
        assertIs<DomainResult.Success<Unit>>(combined.evaluate(1))
    }

    @Test
    fun `and - first violated short-circuits`() {
        var secondEvaluated = false
        val second = DomainPolicy<Int> { secondEvaluated = true; Unit.asSuccess() }
        val combined = alwaysViolated and second
        assertIs<DomainResult.Failure>(combined.evaluate(1))
        kotlin.test.assertFalse(secondEvaluated)
    }

    @Test
    fun `and - second violated returns Failure`() {
        val combined = alwaysSatisfied and alwaysViolated
        assertIs<DomainResult.Failure>(combined.evaluate(1))
    }

    @Test
    fun `or - first satisfied skips second`() {
        var secondEvaluated = false
        val second = DomainPolicy<Int> { secondEvaluated = true; Unit.asSuccess() }
        val combined = alwaysSatisfied or second
        assertIs<DomainResult.Success<Unit>>(combined.evaluate(1))
        kotlin.test.assertFalse(secondEvaluated)
    }

    @Test
    fun `or - first violated evaluates second`() {
        val combined = alwaysViolated or alwaysSatisfied
        assertIs<DomainResult.Success<Unit>>(combined.evaluate(1))
    }

    @Test
    fun `or - both violated returns Failure`() {
        val combined = alwaysViolated or alwaysViolated
        assertIs<DomainResult.Failure>(combined.evaluate(1))
    }

    @Test
    fun `negate - satisfied policy becomes violated`() {
        val negated = alwaysSatisfied.negate { DomainError.Conflict(detail = "negated") }
        assertIs<DomainResult.Failure>(negated.evaluate(1))
    }

    @Test
    fun `negate - violated policy becomes satisfied`() {
        val negated = alwaysViolated.negate { DomainError.Conflict(detail = "negated") }
        assertIs<DomainResult.Success<Unit>>(negated.evaluate(1))
    }

    // ── Triple-chain combinators ───────────────────────────────────────────────

    @Test
    fun `and - triple chain all satisfied returns Success`() {
        val combined = alwaysSatisfied and alwaysSatisfied and alwaysSatisfied
        assertIs<DomainResult.Success<Unit>>(combined.evaluate(1))
    }

    @Test
    fun `and - triple chain third violated returns Failure`() {
        val combined = alwaysSatisfied and alwaysSatisfied and alwaysViolated
        assertIs<DomainResult.Failure>(combined.evaluate(1))
    }

    @Test
    fun `and - triple chain middle violated short-circuits third`() {
        var thirdEvaluated = false
        val third = DomainPolicy<Int> { thirdEvaluated = true; Unit.asSuccess() }
        val combined = alwaysSatisfied and alwaysViolated and third
        assertIs<DomainResult.Failure>(combined.evaluate(1))
        kotlin.test.assertFalse(thirdEvaluated)
    }

    @Test
    fun `or - triple chain all violated returns Failure`() {
        val combined = alwaysViolated or alwaysViolated or alwaysViolated
        assertIs<DomainResult.Failure>(combined.evaluate(1))
    }

    @Test
    fun `or - triple chain last one satisfied returns Success`() {
        val combined = alwaysViolated or alwaysViolated or alwaysSatisfied
        assertIs<DomainResult.Success<Unit>>(combined.evaluate(1))
    }

    // ── or returns the second policy's error when both fail ───────────────────

    @Test
    fun `or - returns second policy error when both fail`() {
        val errorA = DomainError.Conflict(detail = "first-error")
        val errorB = DomainError.Conflict(detail = "second-error")
        val policyA = DomainPolicy<Int> { domainFailure(errorA) }
        val policyB = DomainPolicy<Int> { domainFailure(errorB) }

        val combined = policyA or policyB
        val error = combined.evaluate(1).shouldFailWith<DomainError.Conflict>()
        assertEquals("second-error", error.detail)
    }

    // ── negate error is produced by the lambda ────────────────────────────────

    @Test
    fun `negate - error detail comes from the provided lambda`() {
        val negated = alwaysSatisfied.negate { DomainError.Conflict(detail = "should-not-be-allowed") }
        val error = negated.evaluate(1).shouldFailWith<DomainError.Conflict>()
        assertEquals("should-not-be-allowed", error.detail)
    }

    @Test
    fun `negate lambda is only called when original policy is satisfied`() {
        var lambdaCalled = false
        val negated = alwaysViolated.negate { lambdaCalled = true; DomainError.Conflict(detail = "x") }
        negated.evaluate(1)
        kotlin.test.assertFalse(lambdaCalled)
    }

    // ── Mixed and/or composition ───────────────────────────────────────────────

    @Test
    fun `and combined with or — (satisfied and violated) or satisfied = Success`() {
        val combined = (alwaysSatisfied and alwaysViolated) or alwaysSatisfied
        assertIs<DomainResult.Success<Unit>>(combined.evaluate(1))
    }

    @Test
    fun `or combined with and — (violated or satisfied) and satisfied = Success`() {
        val combined = (alwaysViolated or alwaysSatisfied) and alwaysSatisfied
        assertIs<DomainResult.Success<Unit>>(combined.evaluate(1))
    }
}
