package com.selenus.luna

import kotlin.test.Test
import kotlin.test.assertNotNull

class LunaHeliusClientTest {

    @Test
    fun `test client instantiation`() {
        val client = LunaHeliusClient("fake-api-key", Cluster.DEVNET)
        assertNotNull(client)
        assertNotNull(client.das)
        assertNotNull(client.rpc)
    }
}
