package com.avago.core.data.v2.commands

import com.avago.core.data.domain.PmEndType
import com.avago.core.data.domain.PmIntervalUnit
import com.avago.core.data.domain.PmMeterCondition
import com.avago.core.data.domain.PmPlan
import com.avago.core.data.domain.PmPlanDraft
import com.avago.core.data.domain.PmTrigger
import com.avago.core.data.domain.PmTriggerDraft
import com.avago.core.data.domain.PmTriggerKind
import com.avago.core.data.v2.V2OverlayPatch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class PmCommandBuildersTest {

    private var seq = 0
    private val newId: () -> String = { "id-${seq++}" }

    private val calendar = PmTrigger(
        id = "t-cal", kind = PmTriggerKind.CALENDAR, rrule = "FREQ=MONTHLY", intervalValue = null, intervalUnit = null,
        meterId = null, meterInterval = null, nextDueAt = null, nextDueValue = null, priority = 0, version = 3,
    )
    private val meter = PmTrigger(
        id = "t-met", kind = PmTriggerKind.METER, rrule = null, intervalValue = null, intervalUnit = null,
        meterId = "m1", meterInterval = BigDecimal("250"), nextDueAt = null, nextDueValue = null, priority = 1,
        assetId = "a1", version = 4,
    )
    private val plan = PmPlan(
        id = "p1", title = "Oil change", description = null, assetId = "a1", assetName = "Truck", templateId = null,
        active = true, endType = PmEndType.NEVER, endCount = null, endOn = null, timezone = "UTC", cycleNo = 2,
        lastCompletedAt = null, triggers = listOf(calendar, meter), version = 7,
    )

    @Test
    fun `create sends the triggers and overlays plan and trigger rows`() {
        val draft = PmPlanDraft(
            title = " Inspect ", assetId = "a1", endType = PmEndType.COUNT, endCount = 4,
            triggers = listOf(
                PmTriggerDraft(kind = PmTriggerKind.INTERVAL, intervalValue = 3, intervalUnit = PmIntervalUnit.WEEK),
                PmTriggerDraft(
                    kind = PmTriggerKind.METER, meterId = "m1", meterCondition = PmMeterCondition.AT_LEAST,
                    thresholdValue = BigDecimal("1000"),
                ),
            ),
        )
        val command = PmCommands.createPlan("p-new", draft, newId, "Europe/Oslo", 10, "u")
        assertEquals("maint.pm_plans.create", command.operation)
        val payload = command.payload.jsonObject
        assertEquals("Inspect", payload["title"]!!.jsonPrimitive.content)
        val triggers = payload["triggers"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("interval", "meter"), triggers.map { it["kind"]!!.jsonPrimitive.content })
        assertEquals("week", triggers[0]["interval_unit"]!!.jsonPrimitive.content)
        assertEquals("a1", triggers[1]["asset_id"]!!.jsonPrimitive.content)
        assertEquals("at_least", triggers[1]["meter_condition"]!!.jsonPrimitive.content)

        val ops = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops
        assertEquals(listOf("pm_plans", "pm_triggers", "pm_triggers"), ops.map { it.table })
        assertEquals(4L, (ops[0].set["end_count"] as Number).toLong())
        assertEquals(1_000_000_000L, (ops[2].set["threshold_value_micros"] as Number).toLong())
        assertEquals("p-new", ops[1].set["pm_plan_id"])
    }

    @Test
    fun `create refuses a plan without triggers or a meter trigger without an asset`() {
        assertThrows(IllegalArgumentException::class.java) {
            PmCommands.createPlan("p", PmPlanDraft(title = "x"), newId, null, 0, null)
        }
        val meterOnly = PmPlanDraft(
            title = "x",
            triggers = listOf(PmTriggerDraft(kind = PmTriggerKind.METER, meterId = "m", meterInterval = BigDecimal.TEN)),
        )
        assertThrows(IllegalArgumentException::class.java) { PmCommands.createPlan("p", meterOnly, newId, null, 0, null) }
    }

    @Test
    fun `an unchanged plan sends nothing`() {
        assertTrue(PmCommands.updatePlan(plan, PmPlanDraft.of(plan), newId, 10, "u").isEmpty())
    }

    @Test
    fun `update diffs the plan and replaces, edits, removes and adds triggers`() {
        val draft = PmPlanDraft.of(plan).let { d ->
            d.copy(
                title = "Oil + filter",
                endType = PmEndType.DATE,
                endOn = LocalDate.of(2030, 1, 1),
                triggers = listOf(
                    // calendar trigger edited in place
                    d.triggers[0].copy(rrule = "FREQ=MONTHLY;INTERVAL=2"),
                    // meter trigger turned into an interval: delete + create
                    PmTriggerDraft(id = "t-met", kind = PmTriggerKind.INTERVAL, intervalValue = 90, intervalUnit = PmIntervalUnit.DAY),
                ),
            )
        }
        val commands = PmCommands.updatePlan(plan, draft, newId, 10, "u")
        assertEquals(
            listOf("maint.pm_plans.update", "maint.pm_triggers.delete", "maint.pm_triggers.update", "maint.pm_triggers.create"),
            commands.map { it.operation },
        )
        val planSet = commands[0].payload.jsonObject["set"]!!.jsonObject
        assertEquals(setOf("title", "end_type", "end_on"), planSet.keys)
        assertEquals(7L, commands[0].baseVersion)
        assertEquals("t-met", commands[1].payload.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(4L, commands[1].baseVersion)
        val triggerSet = commands[2].payload.jsonObject["set"]!!.jsonObject
        assertEquals(setOf("rrule"), triggerSet.keys)
        assertEquals(3L, commands[2].baseVersion)
        assertEquals("p1", commands[3].payload.jsonObject["pm_plan_id"]!!.jsonPrimitive.content)
        assertNull(V2OverlayPatch.parse(commands[3].overlayPatch!!)!!.ops.single().set["meter_id"])
    }

    @Test
    fun `switching a meter trigger from interval to threshold clears the interval`() {
        val draft = PmPlanDraft.of(plan).let { d ->
            d.copy(
                triggers = listOf(
                    d.triggers[0],
                    d.triggers[1].copy(meterInterval = null, meterCondition = PmMeterCondition.ABOVE, thresholdValue = BigDecimal("5000")),
                ),
            )
        }
        val command = PmCommands.updatePlan(plan, draft, newId, 10, "u").single()
        assertEquals("maint.pm_triggers.update", command.operation)
        val clear = command.payload.jsonObject["clear"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("meter_interval"), clear)
        val overlay = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops.single().set
        assertTrue(overlay.containsKey("meter_interval_micros") && overlay["meter_interval_micros"] == null)
        assertEquals("above", overlay["meter_condition"])
    }

    @Test
    fun `the asset of a plan cannot change`() {
        assertThrows(IllegalArgumentException::class.java) {
            PmCommands.updatePlan(plan, PmPlanDraft.of(plan).copy(assetId = "a2"), newId, 10, "u")
        }
    }

    @Test
    fun `delete removes the plan and its triggers from the overlay`() {
        val command = PmCommands.deletePlan(plan)
        assertEquals("maint.pm_plans.delete", command.operation)
        assertEquals(7L, command.baseVersion)
        val ops = V2OverlayPatch.parse(command.overlayPatch!!)!!.ops
        assertEquals(listOf("p1", "t-cal", "t-met"), ops.map { it.key["id"] })
    }
}
