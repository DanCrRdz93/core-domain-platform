package com.domain.core.gateway

import com.domain.core.error.DomainError
import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure
import com.domain.core.testing.shouldBeSuccess
import com.domain.core.testing.shouldBeFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GatewayContractTest {

    // ── FlowGateway ────────────────────────────────────────────────────────────

    @Test
    fun `FlowGateway emits multiple results`() = runTest {
        val gateway = object : FlowGateway<String, Int> {
            override fun observe(input: String): Flow<DomainResult<Int>> =
                flowOf(1.asSuccess(), 2.asSuccess(), 3.asSuccess())
        }

        val results = gateway.observe("any").toList()
        assertEquals(3, results.size)
        assertEquals(1, results[0].shouldBeSuccess())
        assertEquals(3, results[2].shouldBeSuccess())
    }

    @Test
    fun `FlowGateway can emit failures in stream`() = runTest {
        val gateway = object : FlowGateway<Unit, String> {
            override fun observe(input: Unit): Flow<DomainResult<String>> = flowOf(
                "ok".asSuccess(),
                domainFailure(DomainError.Infrastructure("connection lost")),
                "recovered".asSuccess(),
            )
        }

        val results = gateway.observe(Unit).toList()
        assertEquals(3, results.size)
        assertEquals("ok", results[0].shouldBeSuccess())
        assertIs<DomainError.Infrastructure>(results[1].shouldBeFailure())
        assertEquals("recovered", results[2].shouldBeSuccess())
    }

    // ── NoParamsFlowGateway ────────────────────────────────────────────────────

    @Test
    fun `NoParamsFlowGateway observes without parameters`() = runTest {
        val gateway = object : NoParamsFlowGateway<Boolean> {
            override fun observe(): Flow<DomainResult<Boolean>> =
                flowOf(true.asSuccess(), false.asSuccess())
        }

        val results = gateway.observe().toList()
        assertEquals(2, results.size)
        assertEquals(true, results[0].shouldBeSuccess())
        assertEquals(false, results[1].shouldBeSuccess())
    }
}
