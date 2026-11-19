package com.avago.feature.schedule

import com.avago.core.data.domain.PmOccurrence
import com.avago.core.data.domain.PmOccurrenceState
import com.avago.feature.schedule.util.PmDueStatus
import com.avago.feature.schedule.util.PmSchedule
import com.avago.feature.schedule.util.ScheduleStatusFilter
import com.avago.feature.schedule.util.ScheduleTypeFilter
import com.avago.feature.schedule.viewmodel.GlobalCalendarViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId

class PmScheduleTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1_000

    private fun occ(
        id: String,
        dueAt: Long? = null,
        dueValue: BigDecimal? = null,
        state: PmOccurrenceState = PmOccurrenceState.OPEN,
        planId: String = "plan",
        cycleNo: Int = 1,
        title: String = id,
    ) = PmOccurrence(
        id = id, planId = planId, planTitle = title, assetId = "a", assetName = "Asset", cycleNo = cycleNo,
        state = state, dueAt = dueAt, dueValue = dueValue, workOrderId = null, openedAt = 0, closedAt = null, version = 1,
    )

    @Test
    fun `status reflects state and due window`() {
        assertEquals(PmDueStatus.OVERDUE, PmSchedule.status(occ("o", dueAt = now - 1), now))
        assertEquals(PmDueStatus.DUE_SOON, PmSchedule.status(occ("o", dueAt = now + 7 * day), now))
        assertEquals(PmDueStatus.ON_TRACK, PmSchedule.status(occ("o", dueAt = now + 7 * day + 1), now))
        assertEquals(PmDueStatus.ON_TRACK, PmSchedule.status(occ("o", dueValue = BigDecimal.TEN), now))
        assertEquals(PmDueStatus.SKIPPED, PmSchedule.status(occ("o", dueAt = now - day, state = PmOccurrenceState.SKIPPED), now))
    }

    @Test
    fun `filter keeps open occurrences, applies filters and sorts by urgency`() {
        val all = listOf(
            occ("later", dueAt = now + 30 * day),
            occ("meter", dueValue = BigDecimal("500")),
            occ("soon", dueAt = now + day),
            occ("late", dueAt = now - day),
            occ("done", dueAt = now - day, state = PmOccurrenceState.COMPLETED),
        )
        assertEquals(listOf("late", "soon", "later", "meter"), PmSchedule.filter(all, ScheduleStatusFilter.ALL, ScheduleTypeFilter.ALL, now).map { it.id })
        assertEquals(listOf("meter"), PmSchedule.filter(all, ScheduleStatusFilter.ALL, ScheduleTypeFilter.BY_METER, now).map { it.id })
        assertEquals(listOf("late", "soon", "later"), PmSchedule.filter(all, ScheduleStatusFilter.ALL, ScheduleTypeFilter.BY_DATE, now).map { it.id })
        assertEquals(listOf("later", "meter"), PmSchedule.filter(all, ScheduleStatusFilter.UPCOMING, ScheduleTypeFilter.ALL, now).map { it.id })
        assertEquals(listOf("late"), PmSchedule.filter(all, ScheduleStatusFilter.OVERDUE, ScheduleTypeFilter.ALL, now).map { it.id })
    }

    @Test
    fun `history lists other cycles of the same plan newest first`() {
        val current = occ("c3", cycleNo = 3)
        val all = listOf(occ("c1", cycleNo = 1), current, occ("c2", cycleNo = 2), occ("x", planId = "other", cycleNo = 9))
        assertEquals(listOf("c2", "c1"), PmSchedule.history(all, current).map { it.id })
    }

    @Test
    fun `parseValue accepts comma decimals and rejects garbage`() {
        assertEquals(BigDecimal("12.5"), PmSchedule.parseValue(" 12,5 "))
        assertNull(PmSchedule.parseValue(""))
        assertNull(PmSchedule.parseValue("abc"))
        assertEquals("1500", PmSchedule.formatValue(BigDecimal("1500.000")))
    }

    @Test
    fun `byDate groups dated occurrences of the month`() {
        val zone = ZoneId.systemDefault()
        val month = YearMonth.of(2026, 3)
        fun at(dayOfMonth: Int) = month.atDay(dayOfMonth).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val list = listOf(
            occ("a", dueAt = at(3)),
            occ("b", dueAt = at(3)),
            occ("c", dueAt = at(20)),
            occ("meter", dueValue = BigDecimal.ONE),
            occ("april", dueAt = month.plusMonths(1).atDay(1).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()),
        )
        val grouped = GlobalCalendarViewModel.byDate(list, month)
        assertEquals(setOf(month.atDay(3), month.atDay(20)), grouped.keys)
        assertEquals(listOf("a", "b"), grouped.getValue(month.atDay(3)).map { it.id })
    }
}
