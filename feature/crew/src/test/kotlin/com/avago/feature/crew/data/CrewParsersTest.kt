package com.avago.feature.crew.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrewParsersTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `passport link accepts label urls, app links and bare ids`() {
        val id = "3F2504E0-4F89-11D3-9A0C-0305E82C3301"
        val expected = PassportLink.Passport(id.lowercase())
        assertEquals(expected, PassportLink.parse(id))
        assertEquals(expected, PassportLink.parse("https://www.avagomate.com/a/$id"))
        assertEquals(expected, PassportLink.parse("https://www.avagomate.com/passport/$id?x=1"))
        assertEquals(expected, PassportLink.parse("avago://passport/$id"))
        assertEquals(
            PassportLink.Claim("abcdefghijklmnop_-12"),
            PassportLink.parse("https://www.avagomate.com/passport/claim/abcdefghijklmnop_-12"),
        )
    }

    @Test
    fun `passport link rejects unrelated or malformed input`() {
        assertNull(PassportLink.parse(null))
        assertNull(PassportLink.parse("  "))
        assertNull(PassportLink.parse("hello world"))
        assertNull(PassportLink.parse("https://example.com/a/not-a-uuid"))
        assertNull(PassportLink.parse("https://www.avagomate.com/passport/claim/short"))
        assertNull(PassportLink.parse("https://www.avagomate.com/pricing"))
    }

    @Test
    fun `machines today parses items and tolerant meter shapes`() {
        val m = MachinesToday.from(
            obj(
                """{"date":"2026-11-20","inspections_pending":2,"items":[
                  {"asset_id":"a1","make":"CAT","model":"320","open_defects":1,"last_meter":{"value":1234.5,"unit":"h"},"inspected_today_result":"pass"},
                  {"asset_id":"a2","name":"Lift","last_meter":88},
                  {"name":"no id"}]}""",
            ),
        )
        assertEquals(2, m.inspectionsPending)
        assertEquals(listOf("a1", "a2"), m.items.map { it.assetId })
        assertEquals("CAT 320", m.items[0].name)
        assertEquals("1234.5 h", m.items[0].lastMeter)
        assertTrue(m.items[0].inspectedToday)
        assertEquals("88", m.items[1].lastMeter)
        assertNull(Machine.meterText(JsonNull))
    }

    @Test
    fun `site checklist reports local problems and reads loose answers`() {
        val c = SiteChecklist.from(
            obj(
                """{"items":["site_access","ppe","ground_conditions"],"lift_plan_required":true,
                   "checklist":{"answers":{"site_access":"yes","ppe":{"ok":false}}}}""",
            ),
        )
        assertEquals(true, c.answers.getValue("site_access").ok)
        assertEquals(
            listOf("ppe_failed_without_note", "ground_conditions_unanswered", "lift_plan_missing"),
            c.localProblems(),
        )
        val fixed = c.copy(
            answers = c.answers + ("ppe" to SiteAnswer(false, "spare helmet")) + ("ground_conditions" to SiteAnswer(true)),
            liftPlanAttached = true,
        )
        assertTrue(fixed.localProblems().isEmpty())
        assertEquals(SiteAnswer(false), SiteChecklist.answerOf(JsonPrimitive(false)))
        assertEquals(SiteAnswer(null), SiteChecklist.answerOf(JsonPrimitive("maybe")))
    }

    @Test
    fun `inspection body sends severity only for failures and drops blanks`() {
        val body = KtorCrewService.inspectionBody(
            listOf(
                InspectionAnswer("brakes", pass = true, severity = "major"),
                InspectionAnswer("tyres", pass = false, note = "cut", severity = "out_of_service", photoIds = listOf("p1")),
                InspectionAnswer("horn", pass = null, note = " "),
            ),
            notes = " ",
            meterValue = 12.5,
        )
        val answers = body["answers"] as JsonArray
        assertNull(answers[0].jsonObject["severity"])
        assertNull(answers[0].jsonObject["photo_ids"])
        assertEquals("out_of_service", (answers[1].jsonObject["severity"] as JsonPrimitive).content)
        assertEquals(JsonNull, answers[2].jsonObject["pass"])
        assertNull(answers[2].jsonObject["note"])
        assertNull(body["notes"])
        assertEquals("12.5", (body["meter_value"] as JsonPrimitive).content)
    }

    @Test
    fun `handover body only includes damage on return`() {
        val input = HandoverInput(
            checklist = listOf(HandoverCheck("fuel", "Fuel", true)),
            photoIds = listOf("p1"),
            fuelLevel = "full",
            damage = listOf(DamageEntry("Dent rear")),
        )
        assertNull(KtorCrewService.handoverBody(input, isReturn = false)["damage"])
        val ret = KtorCrewService.handoverBody(input, isReturn = true)
        assertEquals(1, (ret["damage"] as JsonArray).size)
        assertEquals("full", (ret["fuel_level"] as JsonPrimitive).content)
    }

    @Test
    fun `operator checklist body keeps notes and lift plan flag`() {
        val c = SiteChecklist(
            keys = listOf("ppe", "site_access"),
            liftPlanRequired = true,
            answers = mapOf("ppe" to SiteAnswer(false, "missing gloves"), "site_access" to SiteAnswer(null)),
            liftPlanAttached = true,
            completedAt = null,
            problems = emptyList(),
        )
        val answers = KtorOperatorService.checklistBody(c)["answers"]!!.jsonObject
        val ppe = answers.getValue("ppe").jsonObject
        assertEquals("false", (ppe["ok"] as JsonPrimitive).content)
        assertEquals("missing gloves", (ppe["note"] as JsonPrimitive).content)
        assertNull(answers["site_access"])
        assertEquals("true", (answers["lift_plan_attached"] as JsonPrimitive).content)
    }
}
