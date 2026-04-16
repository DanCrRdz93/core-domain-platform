package com.domain.core.event

import com.domain.core.testing.TEST_NOW_MILLIS
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DomainEventTest {

    private data class OrderPlaced(
        val orderId: String,
        val total: Double,
        override val occurredAt: Long,
    ) : DomainEvent

    private data class UserRegistered(
        val userId: String,
        override val occurredAt: Long,
    ) : DomainEvent

    @Test
    fun `DomainEvent carries occurredAt timestamp`() {
        val event = OrderPlaced("order-1", 99.99, TEST_NOW_MILLIS)
        assertEquals(TEST_NOW_MILLIS, event.occurredAt)
        assertIs<DomainEvent>(event)
    }

    @Test
    fun `DomainEventPublisher receives published events`() = runTest {
        val published = mutableListOf<DomainEvent>()
        val publisher = DomainEventPublisher { event -> published += event }

        publisher.publish(OrderPlaced("o-1", 50.0, TEST_NOW_MILLIS))
        publisher.publish(UserRegistered("u-1", TEST_NOW_MILLIS))

        assertEquals(2, published.size)
        assertIs<OrderPlaced>(published[0])
        assertIs<UserRegistered>(published[1])
    }

    @Test
    fun `DomainEventHandler handles typed events`() = runTest {
        val handled = mutableListOf<OrderPlaced>()
        val handler = DomainEventHandler<OrderPlaced> { event -> handled += event }

        handler.handle(OrderPlaced("o-1", 100.0, TEST_NOW_MILLIS))

        assertEquals(1, handled.size)
        assertEquals("o-1", handled[0].orderId)
    }
}
