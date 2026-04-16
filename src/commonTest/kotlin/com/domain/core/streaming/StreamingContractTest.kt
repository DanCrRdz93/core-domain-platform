package com.domain.core.streaming

import com.domain.core.error.DomainError
import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure
import com.domain.core.testing.shouldBeFailure
import com.domain.core.testing.shouldBeSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class StreamingContractTest {

    // ── ConnectionState ─────────────────────────────────────────────────────────

    @Test
    fun `ConnectionState Connecting default attempt is 0`() {
        val state = ConnectionState.Connecting()
        assertEquals(0, state.attempt)
    }

    @Test
    fun `ConnectionState Connecting tracks attempt number`() {
        val state = ConnectionState.Connecting(attempt = 3)
        assertEquals(3, state.attempt)
    }

    @Test
    fun `ConnectionState Connected is a singleton`() {
        val a = ConnectionState.Connected
        val b = ConnectionState.Connected
        assertEquals(a, b)
    }

    @Test
    fun `ConnectionState Disconnected with null error means intentional close`() {
        val state = ConnectionState.Disconnected()
        assertNull(state.error)
    }

    @Test
    fun `ConnectionState Disconnected carries DomainError`() {
        val error = DomainError.Infrastructure("Connection lost")
        val state = ConnectionState.Disconnected(error = error)
        assertIs<DomainError.Infrastructure>(state.error)
        assertEquals("Connection lost", (state.error as DomainError.Infrastructure).detail)
    }

    @Test
    fun `ConnectionState exhaustive when works`() {
        val states = listOf(
            ConnectionState.Connecting(0),
            ConnectionState.Connected,
            ConnectionState.Disconnected(),
        )
        val labels = states.map { state ->
            when (state) {
                is ConnectionState.Connecting -> "connecting-${state.attempt}"
                is ConnectionState.Connected -> "connected"
                is ConnectionState.Disconnected -> "disconnected"
            }
        }
        assertEquals(listOf("connecting-0", "connected", "disconnected"), labels)
    }

    // ── StreamingConnection ─────────────────────────────────────────────────────

    @Test
    fun `StreamingConnection exposes state and incoming`() = runTest {
        val connection = FakeStreamingConnection(
            initialState = ConnectionState.Connected,
            items = flowOf(1.asSuccess(), 2.asSuccess(), 3.asSuccess()),
        )

        assertIs<ConnectionState.Connected>(connection.state.value)

        val results = connection.incoming.toList()
        assertEquals(3, results.size)
        assertEquals(1, results[0].shouldBeSuccess())
        assertEquals(2, results[1].shouldBeSuccess())
        assertEquals(3, results[2].shouldBeSuccess())
    }

    @Test
    fun `StreamingConnection incoming can emit failures without killing stream`() = runTest {
        val connection = FakeStreamingConnection(
            initialState = ConnectionState.Connected,
            items = flowOf(
                "tick-1".asSuccess(),
                domainFailure(DomainError.Infrastructure("bad frame")),
                "tick-2".asSuccess(),
            ),
        )

        val results = connection.incoming.toList()
        assertEquals(3, results.size)
        assertEquals("tick-1", results[0].shouldBeSuccess())
        assertIs<DomainError.Infrastructure>(results[1].shouldBeFailure())
        assertEquals("tick-2", results[2].shouldBeSuccess())
    }

    @Test
    fun `StreamingConnection close transitions state`() = runTest {
        val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        val connection = FakeStreamingConnection<Unit>(
            stateFlow = stateFlow,
            items = flowOf(),
        )

        connection.close()
        assertIs<ConnectionState.Disconnected>(stateFlow.value)
        assertNull((stateFlow.value as ConnectionState.Disconnected).error)
    }

    // ── BidirectionalStreamingConnection ─────────────────────────────────────────

    @Test
    fun `BidirectionalStreamingConnection can send messages`() = runTest {
        val sent = mutableListOf<String>()
        val connection = FakeBidirectionalConnection<String, String>(
            initialState = ConnectionState.Connected,
            items = flowOf("response".asSuccess()),
            onSend = { msg -> sent.add(msg); DomainResult.Success(Unit) },
        )

        val result = connection.send("hello")
        assertEquals(Unit, result.shouldBeSuccess())
        assertEquals(listOf("hello"), sent)
    }

    @Test
    fun `BidirectionalStreamingConnection send fails when disconnected`() = runTest {
        val connection = FakeBidirectionalConnection<String, String>(
            initialState = ConnectionState.Disconnected(),
            items = flowOf(),
            onSend = { domainFailure(DomainError.Infrastructure("not connected")) },
        )

        val result = connection.send("hello")
        assertIs<DomainError.Infrastructure>(result.shouldBeFailure())
    }

    // ── StreamingGateway ────────────────────────────────────────────────────────

    @Test
    fun `StreamingGateway connect returns a StreamingConnection`() = runTest {
        val gateway = object : StreamingGateway<String, Int> {
            override fun connect(input: String): StreamingConnection<Int> =
                FakeStreamingConnection(
                    initialState = ConnectionState.Connected,
                    items = flowOf(42.asSuccess()),
                )
        }

        val connection = gateway.connect("BTC-USD")
        assertIs<ConnectionState.Connected>(connection.state.value)
        val results = connection.incoming.toList()
        assertEquals(42, results[0].shouldBeSuccess())
    }

    @Test
    fun `NoParamsStreamingGateway connect without parameters`() = runTest {
        val gateway = object : NoParamsStreamingGateway<String> {
            override fun connect(): StreamingConnection<String> =
                FakeStreamingConnection(
                    initialState = ConnectionState.Connected,
                    items = flowOf("notification".asSuccess()),
                )
        }

        val connection = gateway.connect()
        assertIs<ConnectionState.Connected>(connection.state.value)
        assertEquals("notification", connection.incoming.toList()[0].shouldBeSuccess())
    }

    @Test
    fun `BidirectionalStreamingGateway connect returns bidirectional connection`() = runTest {
        val gateway = object : BidirectionalStreamingGateway<String, String, String> {
            override fun connect(input: String): BidirectionalStreamingConnection<String, String> =
                FakeBidirectionalConnection(
                    initialState = ConnectionState.Connected,
                    items = flowOf("welcome to $input".asSuccess()),
                    onSend = { DomainResult.Success(Unit) },
                )
        }

        val connection = gateway.connect("room-42")
        assertIs<ConnectionState.Connected>(connection.state.value)
        assertEquals("welcome to room-42", connection.incoming.toList()[0].shouldBeSuccess())

        val sendResult = connection.send("hello")
        assertEquals(Unit, sendResult.shouldBeSuccess())
    }

    // ── Test doubles ────────────────────────────────────────────────────────────

    private class FakeStreamingConnection<T>(
        stateFlow: MutableStateFlow<ConnectionState>? = null,
        initialState: ConnectionState = ConnectionState.Connected,
        items: Flow<DomainResult<T>>,
    ) : StreamingConnection<T> {
        private val _state = stateFlow ?: MutableStateFlow(initialState)
        override val state: MutableStateFlow<ConnectionState> = _state
        override val incoming: Flow<DomainResult<T>> = items
        override suspend fun close() {
            _state.value = ConnectionState.Disconnected()
        }
    }

    private class FakeBidirectionalConnection<S, T>(
        initialState: ConnectionState = ConnectionState.Connected,
        items: Flow<DomainResult<T>>,
        private val onSend: suspend (S) -> DomainResult<Unit>,
    ) : BidirectionalStreamingConnection<S, T> {
        private val _state = MutableStateFlow(initialState)
        override val state: MutableStateFlow<ConnectionState> = _state
        override val incoming: Flow<DomainResult<T>> = items
        override suspend fun send(message: S): DomainResult<Unit> = onSend(message)
        override suspend fun close() {
            _state.value = ConnectionState.Disconnected()
        }
    }
}
