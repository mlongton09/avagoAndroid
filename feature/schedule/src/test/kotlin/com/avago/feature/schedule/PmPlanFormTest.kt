package com.avago.feature.schedule

import com.avago.core.data.domain.PmEndType
import com.avago.core.data.domain.PmIntervalUnit
import com.avago.core.data.domain.PmPlan
import com.avago.core.data.domain.PmTrigger
import com.avago.core.data.domain.PmTriggerKind
import com.avago.feature.schedule.util.CalendarFreq
import com.avago.feature.schedule.util.MeterMode
import com.avago.feature.schedule.util.PlanForm
import com.avago.feature.schedule.util.PlanFormError
import com.avago.feature.schedule.util.PlanFormResult
import com.avago.feature.schedule.util.RruleHelper
import com.avago.feature.schedule.util.TriggerForm
import com.avago.feature.schedule.util.toDraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PmPlanFormTest {

    private fun valid(form: PlanForm) = (form.toDraft() as PlanFormResult.Valid).draft

    private fun invalid(form: PlanForm) = form.toDraft() as PlanFormResult.Invalid

    @Test
    fun `compose keeps an unchanged rule and drops BY parts when the frequency changes`() {
        val stored = "RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=10"
        assertEquals(stored, RruleHelper.compose(stored, CalendarFreq.WEEKLY, 1))
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO;COUNT=10", RruleHelper.compose(stored, CalendarFreq.WEEKLY, 2))
        assertEquals("RRULE:FREQ=MONTHLY;COUNT=10", RruleHelper.compose(stored, CalendarFreq.MONTHLY, 1))
        assertEquals("FREQ=YEARLY;INTERVAL=3", RruleHelper.compose(null, CalendarFreq.YEARLY, 3))
    }

    @Test
    fun `a new form builds a monthly calendar trigger`() {
        val draft = valid(PlanForm(title = " Grease "))
        assertEquals("Grease", draft.title)
        assertEquals("FREQ=MONTHLY", draft.triggers.single().rrule)
    }

    @Test
    fun `validation points at the plan field or the trigger row`() {
        assertEquals(PlanFormError.TITLE, invalid(PlanForm()).error)
        assertEquals(PlanFormError.NO_TRIGGERS, invalid(PlanForm(title = "x", triggers = emptyList())).error)
        assertEquals(PlanFormError.END_COUNT, invalid(PlanForm(title = "x", endType = PmEndType.COUNT, endCount = "0")).error)
        assertEquals(PlanFormError.END_DATE, invalid(PlanForm(title = "x", endType = PmEndType.DATE)).error)
        val meter = TriggerForm(key = 7, kind = PmTriggerKind.METER, meterId = "m")
        assertEquals(PlanFormError.METER_NEEDS_ASSET to 7L, invalid(PlanForm(title = "x", triggers = listOf(meter))).let { it.error to it.triggerKey })
        val noValue = PlanForm(title = "x", assetId = "a", triggers = listOf(meter))
        assertEquals(PlanFormError.METER_VALUE, invalid(noValue).error)
        val interval = TriggerForm(key = 1, kind = PmTriggerKind.INTERVAL, every = "")
        assertEquals(PlanFormError.TRIGGER_EVERY, invalid(PlanForm(title = "x", triggers = listOf(interval))).error)
    }

    @Test
    fun `fields of other end types and meter modes are not sent`() {
        val meter = TriggerForm(
            key = 0, kind = PmTriggerKind.METER, meterId = "m", meterMode = MeterMode.THRESHOLD, meterInterval = "100", threshold = "2,5",
        )
        val draft = valid(PlanForm(title = "x", assetId = "a", endType = PmEndType.NEVER, endCount = "3", triggers = listOf(meter)))
        assertNull(draft.endCount)
        val t = draft.triggers.single()
        assertNull(t.meterInterval)
        assertEquals(BigDecimal("2.5"), t.thresholdValue)
    }

    @Test
    fun `a stored plan round-trips without changes`() {
        val plan = PmPlan(
            id = "p", title = "Filter", description = null, assetId = "a", assetName = "Pump", templateId = null, active = true,
            endType = PmEndType.NEVER, endCount = null, endOn = null, timezone = null, cycleNo = 1, lastCompletedAt = 5L,
            triggers = listOf(
                PmTrigger("t1", PmTriggerKind.CALENDAR, "FREQ=HOURLY;INTERVAL=6", null, null, null, null, null, null),
                PmTrigger("t2", PmTriggerKind.INTERVAL, null, 2, PmIntervalUnit.WEEK.wire, null, null, null, null),
                PmTrigger("t3", PmTriggerKind.METER, null, null, null, "m", BigDecimal("500"), null, null, assetId = "a"),
            ),
            version = 1,
        )
        val form = PlanForm.of(plan)
        assertNull(form.triggers[0].freq)
        val draft = valid(form)
        assertEquals(com.avago.core.data.domain.PmPlanDraft.of(plan).copy(description = null), draft.copy(description = null))
    }
}
