package com.avago.feature.rentalsops.domain

import com.avago.feature.rentalsops.domain.WidgetCounts.FleetRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class WidgetCountsTest {

    private val now = Instant.parse("2026-06-10T10:00:00Z")
    private val utc: ZoneId = ZoneOffset.UTC

    @Test
    fun `counts out, due today and overdue`() {
        val rows = listOf(
            FleetRow("on_rent", Instant.parse("2026-06-10T17:00:00Z")), // due today
            FleetRow("on_rent", Instant.parse("2026-06-12T17:00:00Z")), // out
            FleetRow("overdue", Instant.parse("2026-06-09T17:00:00Z")), // overdue
            FleetRow("available", null),
            FleetRow("reserved", Instant.parse("2026-06-10T12:00:00Z")),
            FleetRow("in_maintenance", null),
        )
        assertEquals(WidgetCounts.Counts(out = 3, dueToday = 1, overdue = 1), WidgetCounts.compute(rows, now, utc))
    }

    @Test
    fun `stale on_rent past its due time is overdue, not due today`() {
        val rows = listOf(FleetRow("on_rent", Instant.parse("2026-06-10T09:00:00Z")))
        assertEquals(WidgetCounts.Counts(1, 0, 1), WidgetCounts.compute(rows, now, utc))
    }

    @Test
    fun `due today uses the unit's time zone`() {
        // 2026-06-11T01:00Z is still 10 June in New York but 11 June in UTC.
        val due = Instant.parse("2026-06-11T01:00:00Z")
        assertEquals(1, WidgetCounts.compute(listOf(FleetRow("on_rent", due, timeZone = "America/New_York")), now, utc).dueToday)
        assertEquals(0, WidgetCounts.compute(listOf(FleetRow("on_rent", due, timeZone = "UTC")), now, utc).dueToday)
        // Unknown zone falls back to the device zone.
        assertEquals(0, WidgetCounts.compute(listOf(FleetRow("on_rent", due, timeZone = "Mars/Base")), now, utc).dueToday)
    }

    @Test
    fun `missing due time uses the server due_back_today flag and deleted rows are skipped`() {
        val rows = listOf(
            FleetRow("on_rent", null, dueBackToday = true),
            FleetRow("on_rent", null),
            FleetRow("overdue", null, deleted = true),
        )
        assertEquals(WidgetCounts.Counts(2, 1, 0), WidgetCounts.compute(rows, now, utc))
    }

    @Test
    fun `empty store gives zeros`() {
        assertEquals(WidgetCounts.Counts.EMPTY, WidgetCounts.compute(emptyList(), now, utc))
    }

    @Test
    fun `fleet card JSON feeds the widget`() {
        val o = Json.parseToJsonElement(
            """{"asset_id":"a1","name":"EX-12","rental_status":"on_rent","due_back_today":true,
               "current":{"due_at":"2026-06-10T17:00:00Z","order_number":"R-1"},"time_zone":"Europe/Madrid"}""",
        ).jsonObject
        val card = FleetCard.from(o)!!
        assertEquals("EX-12", card.name)
        assertEquals(WidgetCounts.Counts(1, 1, 0), WidgetCounts.compute(listOf(card.toWidgetRow()), now, utc))
    }
}
