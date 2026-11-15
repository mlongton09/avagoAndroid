package com.avago.feature.rentals.ui

import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.PostChargeService
import com.avago.feature.rentals.domain.PostCharge
import com.avago.feature.rentals.domain.PostChargeList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PostChargesViewModelTest {
    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject
    private val listJson = """{"items":[{"id":"p1","status":"billed","amount_minor":100,"currency":"USD"}],"window_days":30}"""

    private class Fake(var listResult: ApiResult<PostChargeList>) : PostChargeService {
        var listCalls = 0
        val bodies = mutableListOf<JsonObject>()
        var createResult: ApiResult<PostCharge> = ApiResult.Err(ApiError(500, "x", "x"))
        var chargeResult: ApiResult<PostCharge> = ApiResult.Err(ApiError(500, "x", "x"))

        override suspend fun list(orderId: String): ApiResult<PostChargeList> { listCalls++; return listResult }
        override suspend fun uploadEvidence(orderId: String, filename: String, contentType: String, bytes: ByteArray): ApiResult<String> =
            ApiResult.Ok("file-1")
        override suspend fun create(orderId: String, body: JsonObject): ApiResult<PostCharge> { bodies += body; return createResult }
        override suspend fun charge(orderId: String, id: String) = chargeResult
        override suspend fun void(orderId: String, id: String, reason: String?) = chargeResult
    }

    private fun ok() = ApiResult.Ok(PostChargeList.from(json(listJson)))

    @Test
    fun `section stays hidden for orders that did not go out`() {
        val fake = Fake(ok())
        val vm = PostChargesViewModel(fake)
        vm.bind("o1", "reserved", "USD")
        assertEquals(PostChargesState.Hidden, vm.state.value)
        vm.bind(null, "on_rent", "USD")
        assertEquals(PostChargesState.Hidden, vm.state.value)
        assertEquals(0, fake.listCalls)
        vm.bind("o1", "returned", "USD")
        assertTrue(vm.state.value is PostChargesState.Ready)
        vm.bind("o1", "returned", "USD")
        assertEquals(1, fake.listCalls)
    }

    @Test
    fun `submit requires evidence and reuses the idempotency key on retry`() {
        val fake = Fake(ok())
        val vm = PostChargesViewModel(fake)
        vm.bind("o1", "on_rent", "USD")
        val now = Instant.now()
        vm.openForm(now.minusSeconds(60))
        vm.update(vm.form.value!!.copy(amount = "12.50"))
        vm.submit(now)
        assertEquals("evidence", vm.errors.value["evidence"])
        assertTrue(fake.bodies.isEmpty())

        vm.attachEvidence("toll.pdf", "application/pdf", ByteArray(3))
        assertEquals("file-1", vm.form.value!!.evidenceFileId)
        vm.submit(now)
        vm.submit(now)
        assertEquals(2, fake.bodies.size)
        assertEquals(fake.bodies[0]["client_uuid"]!!.jsonPrimitive.content, fake.bodies[1]["client_uuid"]!!.jsonPrimitive.content)
        assertTrue(vm.form.value != null)
    }

    @Test
    fun `form amounts are parsed in the bound order currency`() {
        val fake = Fake(ok())
        val vm = PostChargesViewModel(fake)
        vm.bind("o1", "on_rent", "JPY")
        val now = Instant.now()
        vm.openForm(now.minusSeconds(60))
        vm.update(vm.form.value!!.copy(amount = "1200", evidenceFileId = "f"))
        vm.submit(now)
        assertEquals(1200L, fake.bodies.single()["amount_minor"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `successful create closes the form and reloads`() = runTest {
        val fake = Fake(ok()).apply { createResult = ApiResult.Ok(PostCharge.from(json("""{"id":"p2","status":"billed"}"""))!!) }
        val vm = PostChargesViewModel(fake)
        vm.bind("o1", "on_rent", "USD")
        vm.openForm(Instant.now().minusSeconds(60))
        vm.update(vm.form.value!!.copy(amount = "5", evidenceFileId = "f"))
        val event = async(UnconfinedTestDispatcher(testScheduler)) { vm.events.first() }
        vm.submit()
        assertEquals(PostChargeEvent.Done(R.string.rentals_pc_added), event.await())
        assertNull(vm.form.value)
        assertEquals(2, fake.listCalls)
    }

    @Test
    fun `charge needing authentication opens the pay url`() = runTest {
        val fake = Fake(ok()).apply {
            chargeResult = ApiResult.Ok(PostCharge.from(json("""{"id":"p1","status":"requires_action","pay_url":"https://pay/x"}"""))!!)
        }
        val vm = PostChargesViewModel(fake)
        vm.bind("o1", "returned", "USD")
        val event = async(UnconfinedTestDispatcher(testScheduler)) { vm.events.first() }
        vm.charge("p1")
        assertEquals(PostChargeEvent.OpenUrl("https://pay/x"), event.await())
    }

    @Test
    fun `server error codes map to messages`() {
        assertEquals(R.string.rentals_pc_err_window, postChargeErrorRes(ApiError(409, "post_charge_window_closed", "")))
        assertEquals(R.string.rentals_pc_err_invoiced, postChargeErrorRes(ApiError(409, "post_charge_invoiced", "")))
        assertEquals(R.string.rentals_pc_err_offline, postChargeErrorRes(ApiError(0, "offline", "")))
        assertNull(postChargeErrorRes(ApiError(400, "invalid_value", "bad")))
    }
}
