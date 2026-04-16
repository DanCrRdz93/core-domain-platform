package com.domain.core.streaming

import com.domain.core.error.DomainError

/**
 * Domain-level representation of a streaming connection's lifecycle state.
 *
 * Design rationale:
 * - Maps from the Data SDK's `WebSocketState` (`Connecting`, `Connected`, `Disconnected`)
 *   without coupling the domain to WebSocket-specific types.
 * - Transport-agnostic: works for WebSockets, SSE, gRPC streams, or any push-based
 *   connection the Data SDK may add in the future.
 * - Sealed ensures exhaustive `when` handling in ViewModels:
 *   ```kotlin
 *   connection.state.collect { state ->
 *       when (state) {
 *           is ConnectionState.Connecting -> showReconnectingBanner(state.attempt)
 *           is ConnectionState.Connected  -> showConnectedBanner()
 *           is ConnectionState.Disconnected -> showDisconnectedBanner(state.error?.message)
 *       }
 *   }
 *   ```
 * - [Disconnected.error] is `DomainError?` (not `WebSocketError`) — the bridge layer
 *   maps infrastructure errors before they reach the domain.
 */
public sealed class ConnectionState {

    /**
     * The connection is being established or re-established.
     * [attempt] starts at 0 for the initial connection and increments on each
     * reconnection attempt, allowing the UI to display "Reconnecting (attempt 3)…".
     */
    public data class Connecting(val attempt: Int = 0) : ConnectionState()

    /**
     * The connection is active and receiving data.
     */
    public data object Connected : ConnectionState()

    /**
     * The connection is closed — either intentionally or due to an error.
     * - [error] is `null` when the connection was closed intentionally (user navigated away,
     *   `close()` was called).
     * - [error] is non-null when the connection was lost and reconnection is not possible
     *   (e.g., authentication failure, protocol error, max reconnection attempts exhausted).
     */
    public data class Disconnected(val error: DomainError? = null) : ConnectionState()
}
