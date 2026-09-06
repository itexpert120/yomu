package com.itexpert120.yomu.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingBookOpensTest {
    @Test fun observingOrRecreatingDoesNotConsumePendingRequests() {
        var durable = emptyList<String>()
        val queue = PendingBookOpens(persist = { durable = it })
        queue.add("book")
        assertEquals(listOf("book"), queue.state.value)
        val recreated = PendingBookOpens(durable) { durable = it }
        assertEquals(listOf("book"), recreated.state.value)
        recreated.acknowledge("unrelated")
        assertEquals(listOf("book"), recreated.state.value)
        recreated.acknowledge("book")
        assertEquals(emptyList<String>(), durable)
    }

    @Test fun repeatedIntentionalRequestsAreDeliveredIndividually() {
        val queue = PendingBookOpens()
        queue.add("book")
        queue.add("book")
        queue.acknowledge("book")
        assertEquals(listOf("book"), queue.state.value)
    }
}
