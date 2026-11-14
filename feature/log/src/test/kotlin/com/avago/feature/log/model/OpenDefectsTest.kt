package com.avago.feature.log.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** Mirrors avagoweb's OpenDefectsPanel helpers and `deriveDefectsByAsset` / `listDefects` filtering. */
class OpenDefectsTest {

    private fun entry(logId: String, assetId: String, date: String, data: String, category: String = "Inspection") =
        InspectionV2.HistoryEntry(logId, assetId, date, category, InspectionV2.parseDataObject(data))

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
    fun `derives only the asset's inspections with asset-scoped ids, most severe first`() {
        val history = listOf(
            entry("a", "asset", "2026-01-01T00:00:00Z", """{"values":{"wipers":"Monitor","brakes":"Needs Repair"}}"""),
            entry(
                "b", "asset", "2026-01-02T00:00:00Z",
                """{"values":{"tires":"Needs Repair"},"defects":[{"itemId":"tires","key":"tires","label":"Tires","bucket":"NR","severity":"out_of_service"}]}""",
            ),
            entry("c", "other", "2026-01-03T00:00:00Z", """{"values":{"horn":"Needs Repair"}}"""),
            entry("d", "asset", "2026-01-04T00:00:00Z", """{"values":{"lamp":"Needs Repair"}}""", category = "Oil Change"),
        )
        val open = OpenDefects.deriveForAsset(history, "asset")
        assertEquals(listOf("asset:tires", "asset:brakes", "asset:wipers"), open.map { it.id })
        assertEquals(listOf("out_of_service", "major", "minor"), open.map { it.severity })
        assertEquals(listOf("open", "open", "monitoring"), open.map { it.status })
        assertTrue(open.all { it.assetId == "asset" })
    }

    @Test
    fun `a later Normal answer closes a derived defect`() {
        val history = listOf(
            entry("a", "asset", "2026-01-01T00:00:00Z", """{"values":{"brakes":"Needs Repair"}}"""),
            entry("b", "asset", "2026-01-05T00:00:00Z", """{"values":{"brakes":"Normal"}}"""),
        )
        assertTrue(OpenDefects.deriveForAsset(history, "asset").isEmpty())
    }

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
    fun `server rows keep only open, monitoring and deferred, sorted`() {
        fun row(id: String, status: String, severity: String, at: String) = InspectionV2.parseDataObject(
            """{"defect_id":"$id","item_key":"$id","label":"$id","bucket":"NR","severity":"$severity","status":"$status","reported_at":"$at","last_seen_at":"$at"}""",
        )!!
        val rows = JsonArray(
            listOf<JsonObject>(
                row("r1", "repaired", "major", "2026-01-01"),
                row("r2", "deferred", "minor", "2026-01-01"),
                row("r3", "open", "out_of_service", "2025-01-01"),
                row("r4", "monitoring", "major", "2026-02-01"),
            ),
        )
        // Same NR rank for r2/r4 → most recently seen first; out-of-service always leads.
        assertEquals(listOf("r3", "r4", "r2"), OpenDefects.fromServerRows(rows).map { it.id })
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

    @Test
    fun `open target prefers the last inspection that saw the defect and must exist locally`() {
        val d = defect(sourceLogId = "a", lastSeenLogId = "b")
        assertEquals("b", OpenDefects.openTarget(d, setOf("a", "b")))
        assertEquals("a", OpenDefects.openTarget(d, setOf("a")))
        assertNull(OpenDefects.openTarget(d, setOf("z")))
        assertNull(OpenDefects.openTarget(defect(sourceLogId = null, lastSeenLogId = null), setOf("a")))
    }

    @Test
    fun `local status changes drop repaired rows and keep Monitor rows in monitoring`() {
        val list = listOf(defect("x"), defect("y"), defect("m", bucket = InspectionV2.Bucket.M, status = "deferred"))
        assertEquals(listOf("y", "m"), OpenDefects.applyStatus(list, "repaired") { it.id == "x" }.map { it.id })
        assertEquals(listOf("deferred", "open", "deferred"), OpenDefects.applyStatus(list, "deferred") { it.id == "x" }.map { it.status })
        assertEquals("monitoring", OpenDefects.applyStatus(list, "open") { it.id == "m" }.last().status)
        assertEquals(list, OpenDefects.applyStatus(list, "deferred") { it.id == "missing" })
    }

    @Test
    fun `register state comes from the sync metadata marker`() {
        assertEquals(OpenDefects.Register.SUPPORTED, OpenDefects.registerOf(1L))
        assertEquals(OpenDefects.Register.UNSUPPORTED, OpenDefects.registerOf(-1L))
        assertEquals(OpenDefects.Register.UNKNOWN, OpenDefects.registerOf(null))
        assertEquals(OpenDefects.Register.UNKNOWN, OpenDefects.registerOf(0L))
    }

    @Test
    fun `cached register rows are authoritative and carry their local status`() {
        val row = """{"defect_id":"r1","asset_id":"asset","item_key":"brakes","label":"Brakes","bucket":"NR","severity":"major","status":"open","reported_at":"2026-01-01","last_seen_at":"2026-01-01"}"""
        val cached = listOfNotNull(OpenDefects.fromCachedRow(row, "deferred"), OpenDefects.fromCachedRow("not json", "open"))
        assertEquals(listOf("deferred"), cached.map { it.status })
        val view = OpenDefects.localView(
            OpenDefects.Register.SUPPORTED, cached,
            derived = listOf(defect("asset:wipers", lastSeenLogId = "synced")),
            unsyncedLogIds = emptySet(), pending = emptyList(),
        )
        // Derived defects from inspections the server already has are not re-added.
        assertEquals(OpenDefects.Source.SERVER, view.source)
        assertEquals(listOf("r1"), view.defects.map { it.id })
        assertTrue(view.pendingIds.isEmpty())
        // An empty snapshot is authoritative: nothing shown.
        assertTrue(OpenDefects.localView(OpenDefects.Register.SUPPORTED, emptyList(), listOf(defect("asset:x")), emptySet(), emptyList()).defects.isEmpty())
    }

    @Test
    fun `defects from inspections not synced yet join the register as pending`() {
        val reg = defect("r1").copy(key = "brakes")
        val local = defect("asset:tires", lastSeenLogId = "new-log").copy(key = "tires")
        val dup = defect("asset:brakes", lastSeenLogId = "new-log").copy(key = "brakes")
        val view = OpenDefects.localView(OpenDefects.Register.SUPPORTED, listOf(reg), listOf(local, dup), setOf("new-log"), emptyList())
        assertEquals(setOf("r1", "asset:tires"), view.defects.map { it.id }.toSet())
        assertEquals(setOf("asset:tires"), view.pendingIds)
        assertEquals(setOf("asset:tires"), view.derivedIds)
    }

    @Test
    fun `queued changes overlay the list and mark rows pending`() {
        val a = defect("a").copy(key = "ka")
        val b = defect("b").copy(key = "kb")
        val c = defect("c").copy(key = "kc")
        val view = OpenDefects.localView(
            OpenDefects.Register.SUPPORTED, listOf(a, b, c), emptyList(), emptySet(),
            listOf(
                OpenDefects.PendingChange("A", "ka", "deferred", derived = false), // ids compare case-insensitively
                OpenDefects.PendingChange("b", "kb", "repaired", derived = false),
                // A derived change whose inspection has since synced follows its slot to the register row.
                OpenDefects.PendingChange("asset:kc", "kc", "deferred", derived = true),
            ),
        )
        assertEquals(listOf("a" to "deferred", "c" to "deferred"), view.defects.map { it.id to it.status }.sortedBy { it.first })
        assertEquals(setOf("a", "c"), view.pendingIds)
    }

    @Test
    fun `without a register the list is derived and queued derived changes apply`() {
        val derived = listOf(defect("asset:ka").copy(key = "ka"), defect("asset:kb").copy(key = "kb"))
        for (register in listOf(OpenDefects.Register.UNKNOWN, OpenDefects.Register.UNSUPPORTED)) {
            val view = OpenDefects.localView(
                register, cached = listOf(defect("stale-cache")), derived = derived, unsyncedLogIds = emptySet(),
                pending = listOf(OpenDefects.PendingChange("asset:ka", "ka", "repaired", derived = true)),
            )
            assertEquals(OpenDefects.Source.DERIVED, view.source)
            assertEquals(listOf("asset:kb"), view.defects.map { it.id })
            assertEquals(setOf("asset:ka", "asset:kb"), view.derivedIds)
            assertTrue(view.pendingIds.isEmpty()) // the pending row left the list
        }
    }
}