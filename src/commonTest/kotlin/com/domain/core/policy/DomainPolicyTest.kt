package com.domain.core.policy

import com.domain.core.error.DomainError
import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure
import kotlin.test.Test
import kotlin.test.assertIs

class DomainPolicyTest {

    private val alwaysSatisfied: DomainPolicy<Int> = DomainPolicy { Unit.asSuccess() }
    private val alwaysViolated: DomainPolicy<Int> = DomainPolicy {
        domainFailure(DomainError.Conflict("violated"))
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
        val negated = alwaysSatisfied.negate(domainFailure(DomainError.Conflict("negated")))
        assertIs<DomainResult.Failure>(negated.evaluate(1))
    }

    @Test
    fun `negate - violated policy becomes satisfied`() {
        val negated = alwaysViolated.negate(domainFailure(DomainError.Conflict("negated")))
        assertIs<DomainResult.Success<Unit>>(negated.evaluate(1))
    }
}
