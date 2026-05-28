package com.avago.core.ui

import androidx.lifecycle.ViewModel
import com.avago.core.auth.IdentityManager
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScoutViewModel @Inject constructor(
    private val client: AvagoServiceClient,
    private val identity: IdentityManager,
) : ViewModel() {

    suspend fun query(prompt: String): String {
        val accountId = identity.activeAccountId.value ?: return "No active account."
        return when (val result = client.scoutQuery(accountId = accountId, query = prompt)) {
            is NetworkResult.Success -> result.data.skill_name
            is NetworkResult.Error -> "Error: ${result.message}"
            is NetworkResult.Unauthorized -> "Session expired. Please sign in again."
        }
    }
}
