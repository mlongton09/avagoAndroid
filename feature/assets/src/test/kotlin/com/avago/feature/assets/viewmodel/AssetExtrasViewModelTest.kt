package com.avago.feature.assets.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.feature.assets.data.AssetExtrasService
import com.avago.feature.assets.model.ExpiryStatus
import com.avago.feature.assets.model.RecallList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AssetExtrasViewModelTest {
    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private val saved = SavedStateHandle(mapOf("assetId" to "a1"))

    private class Fake : AssetExtrasService {
        var recallList: ApiResult<RecallList> = ApiResult.Err(ApiError(500, "server_error", "boom"))
        val refreshFlags = mutableListOf<Boolean>()
        override suspend fun recalls(assetId: String, refresh: Boolean): ApiResult<RecallList> {
            refreshFlags += refresh
            return recallList
        }
    }

    @Test
    fun `recalls load from the cache first and keep data when a forced refresh fails`() {
        val fake = Fake().apply {
            recallList = ApiResult.Ok(RecallList.from(json("""{"supported":true,"recalls":[{"campaign_number":"24V1"}]}""")))
        }
        val vm = RecallsViewModel(fake, saved)
        assertEquals(1, (vm.state.value as ExtrasLoad.Ready).value.openCount)
        fake.recallList = ApiResult.Err(ApiError(0, "offline", "offline"))
        vm.refresh()
        assertTrue(vm.state.value is ExtrasLoad.Ready)
        assertEquals(listOf(false, true), fake.refreshFlags)
    }

    @Test
    fun `a failed first recall load shows the error`() {
        assertTrue(RecallsViewModel(Fake(), saved).state.value is ExtrasLoad.Failed)
    }

    @Test
    fun `expiry status is computed from the ISO date`() {
        val today = LocalDate.of(2026, 3, 1)
        assertEquals(ExpiryStatus.Overdue, ExpiryStatus.of("2026-02-28", today))
        assertEquals(ExpiryStatus.DueSoon, ExpiryStatus.of("2026-03-01", today))
        assertEquals(ExpiryStatus.DueSoon, ExpiryStatus.of("2026-03-31", today))
        assertEquals(ExpiryStatus.Ok, ExpiryStatus.of("2026-04-01", today))
        assertNull(ExpiryStatus.of(" ", today))
        assertNull(ExpiryStatus.of("31/01/2026", today))
    }
}
