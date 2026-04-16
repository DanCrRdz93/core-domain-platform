package com.domain.core.streaming

import com.domain.core.gateway.Gateway

/**
 * Gateway contract for creating streaming connections with a subscription parameter.
 *
 * Design rationale:
 * - This is the streaming equivalent of [com.domain.core.gateway.FlowGateway]: where
 *   `FlowGateway` returns a plain `Flow`, `StreamingGateway` returns a
 *   [StreamingConnection] that additionally exposes connection [state][ConnectionState].
 * - Use [StreamingGateway] when the domain needs **connection state awareness**
 *   (reconnecting banners, send capability, explicit close). Use `FlowGateway`
 *   when a plain reactive stream suffices.
 * - [connect] returns immediately with a [StreamingConnection]; the actual
 *   network connection starts asynchronously (same as the Data SDK's
 *   `SafeWebSocketExecutor.connect()`).
 *
 * Typical implementations bridge from the Data SDK's `StreamingDataSource`:
 * ```kotlin
 * class PriceStreamGatewayImpl(
 *     private val dataSource: PriceStreamDataSource,
 *     private val mapper: Mapper<PriceTickDto, PriceTick>,
 * ) : StreamingGateway<String, PriceTick> {
 *
 *     override fun connect(input: String): StreamingConnection<PriceTick> {
 *         val wsConnection = dataSource.connect(input)
 *         return DomainStreamingConnectionAdapter(wsConnection, mapper)
 *     }
 * }
 * ```
 *
 * [I] — the subscription parameter (e.g., symbol name, room ID, channel).
 * [O] — the deserialized domain type emitted by the stream.
 */
public interface StreamingGateway<in I, out O> : Gateway {
    public fun connect(input: I): StreamingConnection<O>
}

/**
 * No-params variant of [StreamingGateway] — connect to a global stream that
 * requires no subscription parameter.
 *
 * Typical consumers: system-wide notifications, global chat, app-level
 * connectivity monitoring.
 *
 * [O] — the deserialized domain type emitted by the stream.
 */
public interface NoParamsStreamingGateway<out O> : Gateway {
    public fun connect(): StreamingConnection<O>
}

/**
 * Gateway contract for bidirectional streaming connections where the domain
 * can both receive and send typed messages.
 *
 * Typical consumers: chat rooms, collaborative editors, trading terminals
 * where the domain sends subscribe/unsubscribe commands.
 *
 * [I] — the subscription parameter (e.g., room ID).
 * [S] — the type of messages that can be sent.
 * [O] — the type of messages received.
 */
public interface BidirectionalStreamingGateway<in I, in S, out O> : Gateway {
    public fun connect(input: I): BidirectionalStreamingConnection<S, O>
}
