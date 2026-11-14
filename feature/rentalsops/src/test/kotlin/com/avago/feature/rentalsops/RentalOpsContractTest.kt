package com.avago.feature.rentalsops

import com.avago.feature.rentalsops.data.ApiResult
import com.avago.feature.rentalsops.data.RentalOpsApi
import com.avago.feature.rentalsops.data.RentalOpsEntities
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.domain.DeliveryTask
import com.avago.feature.rentalsops.domain.TaskKind
import com.avago.feature.rentalsops.domain.UnassignedLine
import com.avago.feature.rentalsops.push.RentalOpsPushPrefs
import com.avago.feature.rentalsops.push.RentalOpsRoutes
import com.avago.feature.rentalsops.push.RentalPushRouter
import com.avago.feature.rentalsops.sync.DrainOutcome
import com.avago.feature.rentalsops.sync.RentalOpsOutbox
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Wire-level checks against the examples in AvagoSvc `docs/rentals-api-stage-c.md`
 * (origin/master 6ab0092). Ids shortened with "…" in the doc are filled in so routes validate;
 * every field name and wrapper is verbatim from the doc.
 */
class RentalOpsContractTest {

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    // §5 sync feed wrapper; my-tasks sends a tombstone when a task is reassigned away.
    @Test
    fun `my-tasks page with a reassignment tombstone`() {
        val page = obj(
            """{ "entity": "rental_delivery_task",
                 "items": [ { "id": "7d000000-0000-4000-8000-000000000001", "seq": 1042, "updated_at": "2026-03-02T09:00:00Z",
                              "_deleted": false, "kind": "pickup", "status": "assigned", "order_id": "9f000000-0000-4000-8000-000000000001",
                              "order_number": "R-1042", "order_status": "off_rent_requested",
                              "customer_name": "Builder Ltd", "customer_display_name": "Builder Ltd (HQ)", "customer_phone": "+44 20 7946 0000",
                              "asset_ids": ["5b0c0000-0000-4000-8000-000000000001"], "scheduled_at": "2026-03-02T09:00:00Z" },
                            { "id": "7d000000-0000-4000-8000-000000000002", "seq": 1043, "_deleted": true, "deleted_at": "2026-03-02T09:10:00Z" } ],
                 "next_cursor": "1043", "has_more": false, "server_time": "2026-03-02T09:15:00Z" }""",
        )
        assertEquals("1043", (page["next_cursor"] as kotlinx.serialization.json.JsonPrimitive).content)
        val rows = RentalOpsOutbox.feedRows(RentalOpsEntities.TASK, page["items"]!!.jsonArray.map { it.jsonObject }, now = 1L)
        assertEquals(2, rows.size)
        assertEquals(listOf(false, true), rows.map { it.deleted })
        assertEquals(listOf(1042L, 1043L), rows.map { it.seq })
        val t = DeliveryTask.from(obj(rows[0].json))!!
        assertEquals(TaskKind.PICKUP, t.kind)
        assertEquals("R-1042", t.orderNumber)
        assertEquals("Builder Ltd (HQ)", t.customerName)
        assertEquals("+44 20 7946 0000", t.phone)
        assertEquals("off_rent_requested", t.orderStatus)
    }

    // §4 proof uploads: request body and reserve/replay responses.
    @Test
    fun `proof upload request and responses`() {
        val body = RentalOpsApi.proofUploadBody("5b0e0000-0000-4000-8000-000000000001", "photo", "dropoff.jpg", "image/jpeg")
        assertEquals(setOf("client_uuid", "kind", "filename", "content_type"), body.keys)
        val created = RentalOpsOutbox.ProofReservation.from(
            obj(
                """{ "file_upload_id": "c1", "client_uuid": "5b0e", "kind": "photo", "task_id": "7d", "upload_status": "pending",
                     "upload_url": "https://api.example/dev/storage/upload/accounts/a/photos/c1.jpg", "storage_key": "accounts/a/photos/c1.jpg",
                     "content_type": "image/jpeg", "replayed": false }""",
            ),
        )!!
        assertEquals("c1", created.fileUploadId)
        assertFalse(created.alreadyUploaded)
        // Replay after the bytes landed: nothing to PUT or finalize.
        val done = RentalOpsOutbox.ProofReservation.from(obj("""{"file_upload_id":"c1","upload_status":"uploaded","upload_url":null,"replayed":true}"""))!!
        assertNull(done.uploadUrl)
        assertTrue(done.alreadyUploaded)
        // Replay of a still-pending reservation: a fresh URL, so upload again.
        val pending = RentalOpsOutbox.ProofReservation.from(obj("""{"file_upload_id":"c1","upload_status":"pending","upload_url":"https://x","replayed":true}"""))!!
        assertFalse(pending.alreadyUploaded)
        assertNull(RentalOpsOutbox.ProofReservation.from(obj("""{"upload_url":"https://x"}""")))
    }

    // §2 assignment work queue and assign-unit.
    @Test
    fun `unassigned-lines item and assign-unit body`() {
        val page = obj(
            """{ "items": [ { "line_id": "41000000-0000-4000-8000-000000000001", "line_no": 1, "order_id": "9f000000-0000-4000-8000-000000000001",
                 "order_number": "R-00077", "order_status": "held",
                 "order_version": 7, "source": "storefront", "fulfilment": "delivery", "customer_id": "c1",
                 "customer_name": "Builder Ltd", "class_id": "a1", "class_name": "Mini excavator 1.5t",
                 "description": "Mini excavator 1.5t", "qty": 1, "location_id": "77",
                 "start_at": "2026-04-01T08:00:00Z", "end_at": "2026-04-04T17:00:00Z",
                 "class_booked": true, "class_allocation_kind": "hold", "hold_expires_at": "2026-03-30T08:00:00Z",
                 "class_free_units": 2 } ] }""",
        )
        val item = page["items"]!!.jsonArray[0].jsonObject
        val l = UnassignedLine.from(item)!!
        assertEquals("41000000-0000-4000-8000-000000000001", l.lineId)
        assertEquals("R-00077", l.orderNumber)
        assertEquals(7L, l.orderVersion)
        assertEquals("Mini excavator 1.5t", l.title)
        assertEquals(2, l.classFreeUnits)
        assertEquals(Instant.parse("2026-03-30T08:00:00Z"), l.holdExpiresAt)
        // Cached under line_id (the feed rows key).
        assertEquals("line_id", RentalOpsEntities.pkField(RentalOpsEntities.UNASSIGNED_LINE))
        assertEquals(l.lineId, RentalOpsOutbox.feedRows(RentalOpsEntities.UNASSIGNED_LINE, listOf(item), 0L).single().id)

        // "Pass order_version as version to assign-unit."
        assertEquals(obj("""{ "asset_id": "5b0c", "version": 7 }"""), RentalOpsApi.assignUnitBody("5b0c", l.orderVersion))
        assertEquals(obj("""{ "asset_id": "5b0c" }"""), RentalOpsApi.assignUnitBody("5b0c", null))
    }

    // §1 error table.
    @Test
    fun `not_assigned is a reassignment, never a retry`() {
        val r = ApiResult.fromErrorBody(
            403,
            """{ "error": "task is not assigned to you", "code": "not_assigned", "message": "task is not assigned to you",
                 "details": { "task_id": "7d" } }""",
        )
        assertEquals("not_assigned", r.code)
        assertEquals("7d", (r.details?.get("task_id") as kotlinx.serialization.json.JsonPrimitive).content)
        assertTrue(DrainOutcome.classify(r) is DrainOutcome.Reassigned)
        val denied = ApiResult.fromErrorBody(403, """{"error":"no","code":"permission_denied","message":"no","details":{}}""")
        assertTrue(DrainOutcome.classify(denied) is DrainOutcome.Rejected)
    }

    @Test
    fun `invalid_transition replay of an applied step is treated as done`() {
        val r = ApiResult.fromErrorBody(
            409,
            """{"error":"bad","code":"invalid_transition","message":"cannot arrive from completed","details":{"from":"completed","action":"arrive"}}""",
        )
        assertTrue(RentalOpsOutbox.alreadyApplied(r, setOf("arrived", "completed")))
        val conflict = ApiResult.fromErrorBody(409, """{"error":"busy","code":"allocation_conflict","message":"busy","details":{"class_id":"a1","available_qty":0}}""")
        assertFalse(RentalOpsOutbox.alreadyApplied(conflict, setOf("arrived", "completed")))
        assertTrue(DrainOutcome.classify(conflict) is DrainOutcome.Rejected)
    }

    // §6 statement email.
    @Test
    fun `statement email response and replay`() {
        val first = RentalOpsRepository.emailed(
            obj(
                """{ "document_id": "d4", "number": "ST-20260331-1A2B3C4D", "replayed": false,
                     "email": { "id": "e9", "status": "queued", "to_addrs": ["ap@builder.example"], "subject": "Statement of account 2026-03-31" } }""",
            ),
        )
        assertEquals("ap@builder.example", first.to)
        assertFalse(first.replayed)
        val replay = RentalOpsRepository.emailed(obj("""{"document_id":"d4","replayed":true,"email":{"to_addrs":["a@x.example","b@x.example"]}}"""))
        assertEquals("a@x.example, b@x.example", replay.to)
        assertTrue(replay.replayed)
        assertNull(RentalOpsRepository.emailed(obj("""{"document_id":"d4"}""")).to)
    }

    // §7 notification prefs.
    @Test
    fun `notification prefs read and write shapes`() {
        val prefs = RentalOpsPushPrefs.parse(
            obj(
                """{ "items": [
                     { "event": "rental.task_assigned", "description": "A task was assigned to you", "permission": "rental.drive",
                       "push_enabled": true, "email_enabled": false },
                     { "event": "rental.overdue", "description": "Overdue", "permission": "rental.checkin",
                       "push_enabled": false, "email_enabled": false } ] }""",
            ),
        )
        assertEquals(listOf("rental.task_assigned", "rental.overdue"), prefs.map { it.event })
        assertEquals(listOf(true, false), prefs.map { it.pushEnabled })
        assertTrue(prefs.all { it.event in RentalPushRouter.EVENTS })
        assertEquals(
            obj("""{"items":[{"event":"rental.overdue","push_enabled":true}]}"""),
            RentalOpsApi.notificationPrefBody("rental.overdue", true),
        )
    }

    // §7 push payload `data` (FCM flattens values to strings; loc_args is a JSON string).
    @Test
    fun `push payload from the doc`() {
        val data = mapOf(
            "type" to "rental.task_assigned", "account_id" to "acc-1", "order_id" to "9f000000-0000-4000-8000-000000000001",
            "entity_type" to "rental_delivery_task", "entity_id" to "7d000000-0000-4000-8000-000000000001",
            "task_id" to "7d000000-0000-4000-8000-000000000001", "kind" to "pickup", "number" to "R-1042",
            "title" to "New pickup task", "body" to "Scheduled 2026-03-02 09:00 UTC",
            "category" to "RENTAL_PICKUP",
            "title_loc_key" to "rental_task_assigned_pickup_title", "body_loc_key" to "rental_task_assigned_pickup_body",
            "loc_args" to """["R-1042", "pickup"]""",
        )
        val s = RentalPushRouter.route(data)!!
        assertEquals(RentalPushRouter.Category.PICKUP, s.category)
        assertEquals("rental_pickup", s.category.channelId)
        assertEquals(RentalOpsRoutes.task("7d000000-0000-4000-8000-000000000001"), s.route)
        assertTrue(RentalOpsRoutes.isSafe(s.route))
        assertEquals("R-1042", s.orderNumber)
        assertEquals("acc-1", s.accountId)
        assertEquals(listOf("R-1042", "pickup"), s.locArgs)
        assertEquals("rental_task_assigned_pickup_title", s.titleLocKey)
        // Every documented category maps to a channel.
        listOf(
            "RENTAL_BOOKING_REQUEST", "RENTAL_PICKUP_REQUESTED", "RENTAL_DELIVERY", "RENTAL_PICKUP",
            "RENTAL_TRANSFER", "RENTAL_RETURN_DUE", "RENTAL_INVOICE",
        ).forEach { assertEquals(it, RentalPushRouter.Category.fromWire(it)?.wire) }
    }
}
