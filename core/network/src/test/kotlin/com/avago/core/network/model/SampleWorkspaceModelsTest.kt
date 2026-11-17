package com.avago.core.network.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SampleWorkspaceModelsTest {
    private fun parse(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `decodes bare profiles with and without a saved workspace`() {
        val body = parse(
            """{"profiles":[
                {"key":"rental_de","label":"Vermietung","locale":"de","available":true,"asset_count":42,
                 "source_filename":"x.json","template_account_id":"t1",
                 "workspace":{"account_id":"a1","account_name":"Demo","active":true,"asset_count":40,"available":true,"last_reset_at":null}},
                {"key":"farmer","label":"Farmer","locale":"en","available":false,"asset_count":0,"workspace":null}
            ]}""",
        )
        val profiles = SampleWorkspaceJson.decodeProfiles(body).profiles
        assertEquals(2, profiles.size)
        val rental = profiles[0]
        assertEquals("rental_de", rental.key)
        assertEquals(42L, rental.assetCount)
        assertEquals("a1", rental.workspace?.accountId)
        assertTrue(rental.workspace!!.active)
        assertNull(profiles[1].workspace)
        assertFalse(profiles[1].available)
    }

    @Test
    fun `accepts a success-data envelope`() {
        val body = parse("""{"success":true,"data":{"profiles":[{"key":"fleet","label":"Fleet","locale":"en","available":true,"asset_count":3}]}}""")
        assertEquals("fleet", SampleWorkspaceJson.decodeProfiles(body).profiles.single().key)

        val switched = SampleWorkspaceJson.decodeSwitch(parse("""{"success":true,"data":{"account_id":"a9","action":"created"}}"""))
        assertEquals("a9", switched.accountId)
        assertEquals("created", switched.action)
    }

    @Test
    fun `decodes bare switch result`() {
        val switched = SampleWorkspaceJson.decodeSwitch(
            parse("""{"account_id":"a2","action":"switched","profile_key":"rental_en","analytics_status":"ready"}"""),
        )
        assertEquals("a2", switched.accountId)
        assertEquals("switched", switched.action)
        assertEquals("ready", switched.analyticsStatus)
    }

    @Test
    fun `serializes the switch request with the server field name`() {
        val text = Json.encodeToString(SwitchSampleWorkspaceRequest.serializer(), SwitchSampleWorkspaceRequest("rental_de"))
        assertEquals("""{"profile_key":"rental_de"}""", text)
    }
}
