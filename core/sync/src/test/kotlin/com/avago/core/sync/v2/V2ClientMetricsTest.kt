package com.avago.core.sync.v2

import com.avago.core.data.v2.V2Request
import com.avago.core.data.v2.V2Response
import com.avago.core.data.v2.V2Transport
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class V2ClientMetricsTest {
    private val sent = mutableListOf<V2Request>()
    private var status = 200
    private var now = V2ClientMetrics.FLUSH_INTERVAL_MS
    private val metrics = V2ClientMetrics(
        transport = V2Transport { request -> sent += request; V2Response(status) },
        clock = { now },
    )

    private fun counters(request: V2Request): Map<String, Long> =
        Json.parseToJsonElement(request.body!!).jsonObject.getValue("counters").jsonArray.associate { element ->
            val c = element.jsonObject
            val key = "${c.getValue("name").jsonPrimitive.content}/${c.getValue("entity_type").jsonPrimitive.content}/" +
                c.getValue("outcome").jsonPrimitive.content
            key to c.getValue("value").jsonPrimitive.content.toLong()
        }

    @Test
    fun `counters are batched per interval and kept when the send fails`() = runTest {
        metrics.count("sync_v2_hint", "account", "stale")
        metrics.count("sync_v2_hint", "account", "stale")
        status = 503
        metrics.flushIfDue()
        assertEquals(mapOf("sync_v2_hint/account/stale" to 2L), counters(sent.single()))

        metrics.count("sync_v2_run", "account", "incremental")
        metrics.flushIfDue()
        assertEquals(1, sent.size, "not due again yet")

        status = 200
        now += V2ClientMetrics.FLUSH_INTERVAL_MS
        metrics.flushIfDue()
        assertEquals("POST", sent.last().method)
        assertEquals(V2ClientMetrics.PATH, sent.last().path)
        assertEquals(
            mapOf("sync_v2_hint/account/stale" to 2L, "sync_v2_run/account/incremental" to 1L),
            counters(sent.last()),
            "failed batch is restored and merged",
        )

        now += V2ClientMetrics.FLUSH_INTERVAL_MS
        metrics.flushIfDue()
        assertEquals(2, sent.size, "nothing left to send")
    }

    @Test
    fun `only names the server accepts are counted`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { metrics.count("wo_opened", "account", "x") }
        }
    }
}
