package com.avago.feature.assets.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.feature.assets.data.AssetExtrasService
import com.avago.feature.assets.model.RecallList
import com.avago.feature.assets.model.ToolDetails
import com.avago.feature.assets.model.VehicleDetails
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetExtrasViewModelTest {
    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private val saved = SavedStateHandle(mapOf("assetId" to "a1"))

    private class Fake : AssetExtrasService {
        var lastBody: JsonObject? = null
        var saveCalls = 0
        val remedyCalls = mutableListOf<Triple<String, Boolean, String?>>()
        var tool: ApiResult<ToolDetails> = fail()
        var toolSave: ApiResult<ToolDetails> = fail()
        var vehicle: ApiResult<VehicleDetails> = fail()
        var recallList: ApiResult<RecallList> = fail()

        override suspend fun toolDetails(assetId: String) = tool
        override suspend fun saveToolDetails(assetId: String, body: JsonObject): ApiResult<ToolDetails> {
            saveCalls++
            lastBody = body
            return toolSave
        }
        override suspend fun vehicleDetails(assetId: String) = vehicle
        override suspend fun saveVehicleDetails(assetId: String, body: JsonObject): ApiResult<VehicleDetails> {
            saveCalls++
            lastBody = body
            return vehicle
        }
        override suspend fun recalls(assetId: String, refresh: Boolean) = recallList
        override suspend fun setRemedied(assetId: String, campaign: String, remedied: Boolean, note: String?): ApiResult<RecallList> {
            remedyCalls += Triple(campaign, remedied, note)
            return recallList
        }
    }

    @Test
    fun `tool details load fills the form and a failed first load shows the error`() {
        val fake = Fake().apply { tool = ApiResult.Ok(ToolDetails.from(json("""{"calibration_interval_days":90}"""))) }
        val vm = ToolDetailsViewModel(fake, saved)
        assertTrue(vm.state.value is ExtrasLoad.Ready)
        assertEquals("90", vm.form.value.intervalDays)

        assertTrue(ToolDetailsViewModel(Fake(), saved).state.value is ExtrasLoad.Failed)
    }

    @Test
    fun `invalid tool form is not saved`() {
        val fake = Fake().apply { tool = ApiResult.Ok(ToolDetails.from(json("{}"))) }
        val vm = ToolDetailsViewModel(fake, saved)
        vm.update(vm.form.value.copy(calibratedOn = "yesterday"))
        vm.submit()
        assertEquals(0, fake.saveCalls)
        assertEquals("date", vm.errors.value["calibrated_on"])
        vm.update(vm.form.value.copy(calibratedOn = "2026-01-01"))
        assertTrue(vm.errors.value.isEmpty())
    }

    @Test
    fun `tracker tag conflict becomes a field error`() {
        val fake = Fake().apply {
            tool = ApiResult.Ok(ToolDetails.from(json("{}")))
            toolSave = ApiResult.Err(ApiError(409, "tracker_tag_in_use", "in use"))
        }
        val vm = ToolDetailsViewModel(fake, saved)
        vm.update(vm.form.value.copy(trackerTagId = "TAG-1"))
        vm.submit()
        assertEquals(1, fake.saveCalls)
        assertEquals("in_use", vm.errors.value["tracker_tag_id"])
        assertFalse(vm.busy.value)
    }

    @Test
    fun `successful tool save emits saved and refreshes the form`() = runTest {
        val fake = Fake().apply {
            tool = ApiResult.Ok(ToolDetails.from(json("{}")))
            toolSave = ApiResult.Ok(ToolDetails.from(json("""{"tracker_tag_id":"TAG-1"}""")))
        }
        val vm = ToolDetailsViewModel(fake, saved)
        val notice = async(UnconfinedTestDispatcher(testScheduler)) { vm.notices.first() }
        vm.submit()
        assertEquals(ExtrasNotice.Saved, notice.await())
        assertEquals("TAG-1", vm.form.value.trackerTagId)
    }

    @Test
    fun `vehicle save omits odometer unless the server supports it`() {
        val fake = Fake().apply { vehicle = ApiResult.Ok(VehicleDetails.from(json("{}"))) }
        val vm = VehicleDetailsViewModel(fake, saved)
        vm.update(vm.form.value.copy(odometer = "5000"))
        vm.submit()
        assertFalse(fake.lastBody!!.containsKey("odometer"))

        val supported = Fake().apply { vehicle = ApiResult.Ok(VehicleDetails.from(json("""{"odometer":100}"""))) }
        val vm2 = VehicleDetailsViewModel(supported, saved)
        assertEquals("100", vm2.form.value.odometer)
        vm2.submit()
        assertTrue(supported.lastBody!!.containsKey("odometer"))
    }

    @Test
    fun `recalls mark remedied and keep data when a refresh fails`() {
        val fake = Fake().apply {
            recallList = ApiResult.Ok(RecallList.from(json("""{"supported":true,"recalls":[{"campaign_number":"24V1"}]}""")))
        }
        val vm = RecallsViewModel(fake, saved)
        vm.setRemedied("24V1", true, "fixed")
        assertEquals(listOf(Triple("24V1", true, "fixed")), fake.remedyCalls)
        fake.recallList = ApiResult.Err(ApiError(0, "offline", "offline"))
        vm.refresh()
        assertTrue(vm.state.value is ExtrasLoad.Ready)
    }

    private companion object {
        fun <T> fail(): ApiResult<T> = ApiResult.Err(ApiError(500, "server_error", "boom"))
    }
}
