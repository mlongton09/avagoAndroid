package com.avago.feature.log.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/** Mirrors avagoweb's OpenDefectsPanel helpers. */
class OpenDefectsTest {

    private fun defect(
        id: String = "d",
        severity: String = InspectionV2.SEV_MAJOR,
        bucket: InspectionV2.Bucket = InspectionV2.Bucket.NR,
        lastSeenAt: String = "2026-01-01",
        status: String = "open",
        sourceLogId: String? = "a",
        lastSeenLogId: String? = "b",
    ) = InspectionV2.OpenDefect(
        id = id, assetId = "asset", itemId = id, key = id, label = id, cornerLabel = null, sectionTitle = null,
        bucket = bucket, severity = severity, note = null, location = null, sourceLogId = sourceLogId,
        reportedAt = "2026-01-01", lastSeenLogId = lastSeenLogId, lastSeenAt = lastSeenAt, status = status,
    )

    @Test
    fun `ties within a severity rank sort by most recently seen`() {
        val sorted = InspectionV2.sortOpen(
            listOf(
                defect("old", lastSeenAt = "2026-01-01"),
                defect("minor", severity = InspectionV2.SEV_MINOR, bucket = InspectionV2.Bucket.M, lastSeenAt = "2026-03-01"),
                defect("new", lastSeenAt = "2026-02-01"),
                defect("oos", severity = InspectionV2.SEV_OUT_OF_SERVICE, lastSeenAt = "2025-01-01"),
            ),
        )
        assertEquals(listOf("oos", "new", "old", "minor"), sorted.map { it.id })
    }

    @Test
    fun `tone follows web severityColor`() {
        assertEquals(OpenDefects.Tone.RED, OpenDefects.tone(defect(severity = InspectionV2.SEV_OUT_OF_SERVICE, bucket = InspectionV2.Bucket.M)))
        assertEquals(OpenDefects.Tone.ORANGE, OpenDefects.tone(defect(severity = InspectionV2.SEV_MINOR, bucket = InspectionV2.Bucket.NR)))
        assertEquals(OpenDefects.Tone.ORANGE, OpenDefects.tone(defect(severity = InspectionV2.SEV_MAJOR, bucket = InspectionV2.Bucket.M)))
        assertEquals(OpenDefects.Tone.AMBER, OpenDefects.tone(defect(severity = InspectionV2.SEV_MINOR, bucket = InspectionV2.Bucket.M)))
    }

    @Test
    fun `age in whole days, clamped and tolerant of date-only and bad input`() {
        val now = Instant.parse("2026-01-10T12:00:00Z").toEpochMilli()
        assertEquals(0, OpenDefects.ageDays("2026-01-10T01:00:00Z", now))
        assertEquals(1, OpenDefects.ageDays("2026-01-09T11:59:59Z", now))
        assertEquals(9, OpenDefects.ageDays("2026-01-01", now))
        assertEquals(3, OpenDefects.ageDays("2026-01-07T12:00:00+00:00", now))
        assertEquals(0, OpenDefects.ageDays("2026-02-01T00:00:00Z", now))
        assertEquals(0, OpenDefects.ageDays("", now))
        assertEquals(0, OpenDefects.ageDays("not a date", now))
        assertEquals(0, OpenDefects.ageDays(null, now))
    }

    @Test
    fun `collapses to four rows until expanded`() {
        val list = (1..6).toList()
        assertEquals(listOf(1, 2, 3, 4), OpenDefects.visible(list, expanded = false))
        assertEquals(list, OpenDefects.visible(list, expanded = true))
        assertEquals(listOf(1, 2), OpenDefects.visible(listOf(1, 2), expanded = false))
    }
}
