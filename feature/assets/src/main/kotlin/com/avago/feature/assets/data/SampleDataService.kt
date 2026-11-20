package com.avago.feature.assets.data

import com.avago.core.auth.IdentityManager
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.JsonApi
import com.avago.core.network.json.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-run sample data: the server clones its demo account into the caller's account
 * (`POST /accounts/{a}/load-sample-data`, decisions L38); the new rows arrive through sync.
 */
interface SampleDataService {
    suspend fun load(): ApiResult<Unit>
}

@Singleton
class KtorSampleDataService @Inject constructor(
    private val api: JsonApi,
    private val identity: IdentityManager,
) : SampleDataService {
    override suspend fun load(): ApiResult<Unit> {
        val a = identity.getActiveAccountId() ?: return ApiResult.Err(ApiError(-1, "no_account", "No active account"))
        // The clone can take a while on a large demo account.
        return api.post("/accounts/$a/load-sample-data", timeoutMs = LOAD_TIMEOUT_MS).map { }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 120_000L
    }
}
