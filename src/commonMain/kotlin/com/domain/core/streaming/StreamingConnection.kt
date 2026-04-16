package com.domain.core.streaming

import com.domain.core.result.DomainResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain contract for a managed streaming connection that produces typed values
 * over time with connection state awareness.
 *
 * Design rationale:
 * - This is the domain equivalent of the Data SDK's `WebSocketConnection`, but
 *   transport-agnostic: it works for WebSockets, SSE, gRPC streams, or any
 *   push-based connection.
 * - [incoming] emits `DomainResult<T>` (not raw frames) — the bridge layer
 *   deserializes and maps errors before they reach the domain.
 * - [state] is a `StateFlow` so the ViewModel always has the latest connection
 *   status (no need to collect from the beginning).
 * - Each emission in [incoming] can independently succeed or fail without
 *   cancelling the stream — a single malformed message doesn't kill the
 *   entire connection.
 *
 * Mapping from Data SDK:
 * ```
 * WebSocketConnection         →  StreamingConnection<T>
 *   state: StateFlow<WsState> →    state: StateFlow<ConnectionState>
 *   incoming: Flow<WsFrame>   →    incoming: Flow<DomainResult<T>>
 *   close()                   →    close()
 * ```
 *
 * [T] — the deserialized domain type emitted by the stream.
 */
public interface StreamingConnection<out T> {

    /**
     * Current connection lifecycle state.
     * Emits [ConnectionState.Connecting], [ConnectionState.Connected],
     * or [ConnectionState.Disconnected] as the connection evolves.
     */
    public val state: StateFlow<ConnectionState>

    /**
     * Stream of deserialized domain values wrapped in [DomainResult].
     * - Successful emissions: `DomainResult.Success(value)`
     * - Deserialization / validation failures: `DomainResult.Failure(error)`
     *   without cancelling the stream.
     *
     * The stream completes when the connection is closed (either by calling
     * [close] or by the server closing the connection without reconnection).
     */
    public val incoming: Flow<DomainResult<T>>

    /**
     * Gracefully closes the connection.
     * After calling this, [state] transitions to [ConnectionState.Disconnected]
     * with a `null` error (intentional close).
     */
    public suspend fun close()
}

/**
 * Extension of [StreamingConnection] that supports sending messages back to the
 * server — e.g., subscribe/unsubscribe commands, chat messages, trading orders.
 *
 * Design rationale:
 * - Separated from [StreamingConnection] because many streaming use cases are
 *   observe-only (price tickers, notifications, live scores). Forcing every
 *   connection to expose `send()` would violate ISP.
 * - [send] returns `DomainResult<Unit>` to report failures without throwing:
 *   the connection might be in `Disconnected` state, or the message might be
 *   too large.
 *
 * Mapping from Data SDK:
 * ```
 * WebSocketConnection.send(frame)     →  send(message: S)
 * WebSocketConnection.sendText(text)  →  send(message: S)  (S is the domain type)
 * ```
 *
 * [S] — the type of messages that can be sent (input).
 * [T] — the type of messages received (output).
 */
public interface BidirectionalStreamingConnection<in S, out T> : StreamingConnection<T> {

    /**
     * Sends a typed message to the server.
     * Returns [DomainResult.Success] if the message was sent, or
     * [DomainResult.Failure] if the connection is closed or the send failed.
     */
    public suspend fun send(message: S): DomainResult<Unit>
}
