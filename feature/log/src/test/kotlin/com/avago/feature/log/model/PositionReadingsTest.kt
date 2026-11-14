package com.avago.feature.log.model

import com.avago.feature.log.model.PositionReadings.ReadingState
import com.avago.feature.log.model.PositionReadings.WheelState
import com.avago.feature.log.model.VehicleSketchModel.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Parity with web `fieldPacks.ts` / `thresholds.ts` / `PositionReadings.tsx`. */
class PositionReadingsTest {

    // ── Thresholds / state mapping ─────────────────────────────────────────────

    @Test
    fun `pressure window follows the asset profile`() {
        assertEquals(ReadingState.OK, PositionReadings.pressureState(32.0, "psi", "car"))
        assertEquals(ReadingState.OUT, PositionReadings.pressureState(50.0, "psi", "light_vehicle"))
        assertEquals(ReadingState.OUT, PositionReadings.pressureState(27.9, "psi", "LightVehicle"))
        assertEquals(ReadingState.OK, PositionReadings.pressureState(100.0, "psi", "commercial_vehicle"))
        assertEquals(ReadingState.OUT, PositionReadings.pressureState(60.0, "psi", "bus"))
        // Unknown asset type only catches typos.
        assertEquals(ReadingState.OK, PositionReadings.pressureState(140.0, "psi", null))
        assertEquals(ReadingState.OUT, PositionReadings.pressureState(999.0, "psi", "spaceship"))
        assertNull(PositionReadings.pressureState(null, "psi", "car"))
    }

    @Test
    fun `pressure converts to kPa and bar like web`() {
        // light: 28–45 psi → kPa rounded to 5, bar to 2 decimals.
        assertEquals(PositionReadings.Range(195.0, 310.0), PositionReadings.pressureRange("kPa", "car"))
        assertEquals(PositionReadings.Range(1.93, 3.1), PositionReadings.pressureRange("bar", "car"))
        assertEquals(ReadingState.OK, PositionReadings.pressureState(2.2, "bar", "car"))
        assertEquals(ReadingState.OUT, PositionReadings.pressureState(180.0, "kPa", "car"))
        // Blank / unrecognised units default to psi (web `normalizeUnit(unit) ?? 'psi'`).
        assertEquals(ReadingState.OK, PositionReadings.pressureState(32.0, "", "car"))
        assertEquals(ReadingState.OUT, PositionReadings.pressureState(999.0, "furlongs", "car"))
        // A recognised non-pressure unit is never judged out.
        assertEquals(ReadingState.OK, PositionReadings.pressureState(999.0, "mm", "car"))
    }

    @Test
    fun `tread minimum is stricter on the steer axle`() {
        assertEquals(ReadingState.OUT, PositionReadings.treadState(3.0, "32nds", "fl"))
        assertEquals(ReadingState.OK, PositionReadings.treadState(3.0, "32nds", "rl"))
        assertEquals(ReadingState.OUT, PositionReadings.treadState(1.5, "32nds", "rlo"))
        assertEquals(ReadingState.OUT, PositionReadings.treadState(40.0, "32nds", "rr"))
        assertEquals(ReadingState.OUT, PositionReadings.treadState(2.5, "mm", "fr"))
        assertEquals(ReadingState.OK, PositionReadings.treadState(1.6, "mm", "rr"))
        assertEquals(ReadingState.OK, PositionReadings.treadState(5.0, null, "fl"))
        assertNull(PositionReadings.treadState(null, "mm", "fl"))
    }

    @Test
    fun `brake pad below 3 mm is out`() {
        assertEquals(ReadingState.OUT, PositionReadings.padState(2.9))
        assertEquals(ReadingState.OK, PositionReadings.padState(3.0))
        assertNull(PositionReadings.padState(null))
    }

    @Test
    fun `position state - out wins, ok when any metric judged`() {
        val m = listOf(
            PositionReadings.Metric("tread", mapOf("fl" to 6.0, "fr" to 2.0)) { v, p -> PositionReadings.treadState(v, "32nds", p) },
            PositionReadings.Metric("psi", mapOf("fl" to 32.0, "rl" to 32.0)) { v, _ -> PositionReadings.pressureState(v, "psi", "car") },
            PositionReadings.Metric("rotor", mapOf("rr" to 20.0)),
        )
        assertEquals(ReadingState.OK, PositionReadings.positionState(m, "fl"))
        assertEquals(ReadingState.OUT, PositionReadings.positionState(m, "fr"))
        assertEquals(ReadingState.OK, PositionReadings.positionState(m, "rl"))
        // Unjudged metric (rotor) alone gives no state.
        assertNull(PositionReadings.positionState(m, "rr"))
    }

    @Test
    fun `corner states - duals share a corner, out beats ok, selection tints ok`() {
        val positions = PositionReadings.positionsFor("bus", withSpare = true)
        val m = listOf(
            PositionReadings.Metric("psi", mapOf("rlo" to 100.0, "rli" to 40.0, "rro" to 100.0)) { v, _ ->
                PositionReadings.pressureState(v, "psi", "bus")
            },
        )
        val corners = PositionReadings.cornerStates(positions, m, selected = setOf("fl", "spare"))
        assertEquals(WheelState.OUT, corners["rl"])
        assertEquals(WheelState.OK, corners["rr"])
        assertEquals(WheelState.OK, corners["fl"]) // selected, no reading
        assertNull(corners["fr"])
        assertFalse(corners.containsKey("spare"))
    }

    @Test
    fun `wheel state for drawn wheels`() {
        val s = mapOf("fl" to WheelState.OK, "fr" to WheelState.OUT)
        assertNull(PositionReadings.wheelState(emptyList(), s))
        assertEquals(WheelState.OK, PositionReadings.wheelState(listOf("fl"), s))
        assertEquals(WheelState.OUT, PositionReadings.wheelState(listOf("fl", "fr"), s))
        assertEquals(WheelState.EMPTY, PositionReadings.wheelState(listOf("rl"), s))
    }

    // ── Positions / layout ─────────────────────────────────────────────────────

    @Test
    fun `positions for asset and axle`() {
        assertEquals(listOf("fl", "fr", "rl", "rr"), PositionReadings.positionsFor("car"))
        assertEquals(listOf("fl", "fr", "rlo", "rli", "rri", "rro", "spare"), PositionReadings.positionsFor("commercial_vehicle", true))
        assertEquals(listOf("fl", "fr"), PositionReadings.positionsForAxle("front"))
        assertEquals(listOf("rlo", "rli", "rri", "rro"), PositionReadings.positionsForAxle("rear", PositionReadings.positionsFor("bus", true)))
        assertEquals(listOf("fl", "fr", "rl", "rr"), PositionReadings.positionsForAxle("all", PositionReadings.positionsFor("car", true)))
        assertEquals("rl", PositionReadings.cornerOf("rli"))
        assertEquals("rr", PositionReadings.cornerOf("rro"))
        assertNull(PositionReadings.cornerOf("spare"))
    }

    @Test
    fun `layout splits left and right like web`() {
        val car = PositionReadings.layout(PositionReadings.positionsFor("car", true))
        assertEquals(listOf("fl", "rl"), car.left)
        assertEquals(listOf("fr", "rr"), car.right)
        assertTrue(car.spare)
        assertEquals(2, car.rows)
        val bus = PositionReadings.layout(PositionReadings.positionsFor("bus"))
        assertEquals(listOf("fl", "rlo", "rli"), bus.left)
        assertEquals(listOf("fr", "rri", "rro"), bus.right)
        assertFalse(bus.spare)
        assertEquals(3, bus.rows)
        assertEquals(1, PositionReadings.layout(emptyList()).rows)
    }

    @Test
    fun `display positions keep stored data visible in canonical order`() {
        val p = PositionReadings.displayPositions(PositionReadings.positionsFor("car", true), listOf("rli", "bogus", "fl"))
        assertEquals(listOf("fl", "fr", "rl", "rr", "rli", "spare"), p)
    }

    // ── Pack panels / attribute round trip ────────────────────────────────────

    @Test
    fun `tire panel from stored web attributes`() {
        val attrs = LogPacks.parse(
            """{"tire_positions":["fl","fr"],"tire_tread":{"fl":6,"fr":"3"},"tire_psi":{"fl":32,"rr":20},
               "tire_tread_unit":"32nds","tire_pressure_unit":"psi"}""",
        )
        val panel = PositionReadings.tireFromAttrs(attrs, "car")!!
        assertEquals(listOf("fl", "fr", "rl", "rr", "spare"), panel.positions)
        assertEquals(setOf("fl", "fr"), panel.selected)
        assertEquals(ReadingState.OK, panel.state("fl"))
        assertEquals(ReadingState.OUT, panel.state("fr")) // 3/32" on steer axle
        assertEquals(ReadingState.OUT, panel.state("rr")) // 20 psi on a car
        assertEquals(ReadingState.OUT, panel.metricState("psi", "rr"))
        assertNull(panel.metricState("tread", "rr"))
        assertEquals(mapOf("fl" to WheelState.OK, "fr" to WheelState.OUT, "rr" to WheelState.OUT), panel.corners)
        assertEquals(listOf("fl", "fr", "rr"), panel.withData())
        assertEquals("/32\"", panel.specs.first { it.key == "tread" }.unit)
    }

    @Test
    fun `tire panel defaults units and judges metric pressure`() {
        val p = PositionReadings.tirePanel("car", emptySet(), emptyMap(), mapOf("fl" to 220.0), null, "kPa")
        assertEquals("/32\"", p.specs[0].unit)
        assertEquals("kPa", p.specs[1].unit)
        assertEquals(ReadingState.OK, p.state("fl"))
    }

    @Test
    fun `brake panel filters by axle and keeps stored readings`() {
        val front = PositionReadings.brakePanel("car", "front", mapOf("fl" to 2.0), emptyMap())
        assertEquals(listOf("fl", "fr"), front.positions)
        assertEquals(ReadingState.OUT, front.state("fl"))
        assertNull(front.selected)
        val withRear = PositionReadings.brakePanel("car", "front", mapOf("rl" to 8.0), mapOf("fr" to 22.0))
        assertEquals(listOf("fl", "fr", "rl"), withRear.positions)
        assertNull(withRear.state("fr")) // rotor is not judged
        assertEquals(PositionReadings.brakePanel("car", null, emptyMap(), emptyMap()).positions, listOf("fl", "fr", "rl", "rr"))
        assertNull(PositionReadings.brakeFromAttrs(LogPacks.parse("""{"brake_axle":"front"}"""), "car"))
    }

    @Test
    fun `form text parsing accepts comma decimals and drops blanks`() {
        assertEquals(mapOf("fl" to 6.5, "fr" to 32.0), PositionReadings.parseAll(mapOf("fl" to "6,5", "fr" to " 32 ", "rl" to "", "rr" to "x")))
        // Same keys / shape as the web writer (numMap).
        assertEquals("""{"fl":6.5}""", LogPacks.numMap(mapOf("fl" to "6,5")).toString())
    }

    // ── Sketch targets ─────────────────────────────────────────────────────────

    @Test
    fun `wheel targets reach the minimum size and never overlap`() {
        for (kind in Kind.entries) {
            val min = 48f / 1.2f
            val targets = PositionReadings.wheelTargets(kind, min)
            assertTrue(targets.isNotEmpty(), "$kind has targets")
            assertEquals(targets.map { it.corner }.distinct(), targets.map { it.corner }, "$kind one target per corner")
            for (i in targets.indices) for (j in i + 1 until targets.size) {
                assertFalse(targets[i].overlaps(targets[j]), "$kind ${targets[i].corner}/${targets[j].corner} overlap")
            }
            targets.forEach { assertTrue(it.w >= min - 0.01f, "$kind ${it.corner} width") }
        }
        val moto = PositionReadings.wheelTargets(Kind.MOTO, 40f)
        assertEquals(listOf(listOf("fl", "fr"), listOf("rl", "rr")), moto.map { it.corners })
        val box = PositionReadings.wheelTargets(Kind.BOX_TRUCK, 40f)
        assertEquals(listOf("fl", "fr", "rl", "rr"), box.map { it.corner })
    }
}
