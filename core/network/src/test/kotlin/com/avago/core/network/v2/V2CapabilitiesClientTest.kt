package com.avago.core.network.v2

import com.avago.core.network.v2.V2CapabilitiesClient.Companion.classify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class V2CapabilitiesClientTest {

    @Test
    fun `200 with client version in range is compatible`() {
        assertEquals(V2ProbeResult.COMPATIBLE, classify(200, """{"contract_version":{"min":1,"max":2}}""", 1))
        val flat = """{"min_contract_version":1,"max_contract_version":1}"""
        assertEquals(V2ProbeResult.COMPATIBLE, classify(200, flat, 1))
    }

    @Test
    fun `out of range or 426 is incompatible`() {
        assertEquals(V2ProbeResult.INCOMPATIBLE, classify(200, """{"contract_version":{"min":2,"max":3}}""", 1))
        assertEquals(V2ProbeResult.INCOMPATIBLE, classify(426, """{"error":"upgrade_required"}""", 1))
    }

    @Test
    fun `missing v2 endpoint means legacy server`() {
        assertEquals(V2ProbeResult.LEGACY, classify(404, "", 1))
        assertEquals(V2ProbeResult.LEGACY, classify(405, "", 1))
    }

    @Test
    fun `server errors and unparseable bodies are unreachable`() {
        assertEquals(V2ProbeResult.UNREACHABLE, classify(503, "", 1))
        assertEquals(V2ProbeResult.UNREACHABLE, classify(401, "", 1))
        assertEquals(V2ProbeResult.UNREACHABLE, classify(200, "<html>", 1))
        assertEquals(V2ProbeResult.UNREACHABLE, classify(200, """{"projections":[]}""", 1))
    }
}
