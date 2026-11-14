package com.avago.feature.rentalsops.sync

import com.avago.feature.rentalsops.data.ApiResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrainOutcomeTest {

    @Test
    fun `ok is done and transport is retried`() {
        assertEquals(DrainOutcome.Done, DrainOutcome.classify(ApiResult.Ok(JsonObject(emptyMap()))))
        assertTrue(DrainOutcome.classify(ApiResult.Transport("timeout")) is DrainOutcome.Retry)
    }

    @Test
    fun `undeployed endpoints, auth refresh and server errors are retried, not rejected`() {
        listOf(401, 404, 405, 408, 429, 500, 501, 503).forEach { s ->
            assertTrue(DrainOutcome.classify(ApiResult.Http(s, null, "x")) is DrainOutcome.Retry, "status $s")
        }
    }

    @Test
    fun `business errors are rejected with the server code`() {
        val r = DrainOutcome.classify(ApiResult.Http(400, "proof_required", "need proof"))
        assertEquals(DrainOutcome.Rejected("proof_required: need proof"), r)
        assertTrue(DrainOutcome.classify(ApiResult.Http(403, "forbidden", "no")) is DrainOutcome.Rejected)
        assertTrue(DrainOutcome.classify(ApiResult.Http(409, "allocation_conflict", "busy")) is DrainOutcome.Rejected)
    }

    private fun conflict(from: String?, code: String = "invalid_transition", status: Int = 409) =
        ApiResult.Http(status, code, "bad", from?.let { JsonObject(mapOf("from" to JsonPrimitive(it))) })

    @Test
    fun `replayed transition whose step already happened counts as applied`() {
        // Arrive replayed after the server already moved the task to arrived/completed.
        assertTrue(RentalOpsOutbox.alreadyApplied(conflict("arrived"), setOf("arrived", "completed")))
        assertTrue(RentalOpsOutbox.alreadyApplied(conflict("completed"), setOf("arrived", "completed")))
    }

    @Test
    fun `genuine invalid transitions and other conflicts are not swallowed`() {
        val reached = setOf("arrived", "completed")
        assertFalse(RentalOpsOutbox.alreadyApplied(conflict("cancelled"), reached))
        assertFalse(RentalOpsOutbox.alreadyApplied(conflict(null), reached))
        assertFalse(RentalOpsOutbox.alreadyApplied(conflict("arrived", code = "allocation_conflict"), reached))
        assertFalse(RentalOpsOutbox.alreadyApplied(conflict("arrived", status = 400), reached))
        assertFalse(RentalOpsOutbox.alreadyApplied(ApiResult.Transport("x"), reached))
    }
}
