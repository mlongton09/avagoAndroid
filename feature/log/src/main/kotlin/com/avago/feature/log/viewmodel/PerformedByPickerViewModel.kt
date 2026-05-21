package com.avago.feature.log.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.UserEntity
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.model.UserResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PerformedByPickerUiState(
    val members: List<UserEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    val freeTextName: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PerformedByPickerViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    private val serviceClient: AvagoServiceClient,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _freeText = MutableStateFlow("")

    val freeText: StateFlow<String> = _freeText.asStateFlow()
    val query: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<PerformedByPickerUiState> =
        identity.activeAccountId.filterNotNull()
            .flatMapLatest { accountId ->
                try {
                    combine(
                        dbFactory.get(accountId).userDao().observeAll(accountId),
                        _query,
                        _isLoading,
                        _error,
                        _freeText,
                    ) { users, query, loading, error, freeText ->
                        val filtered = users
                            .filter { it.isActive }
                            .filter {
                                query.isBlank() ||
                                    it.displayName?.contains(query, ignoreCase = true) == true ||
                                    it.email?.contains(query, ignoreCase = true) == true
                            }
                            .sortedBy { it.displayName ?: "" }
                        PerformedByPickerUiState(
                            members = filtered,
                            isLoading = loading,
                            error = error,
                            query = query,
                            freeTextName = freeText,
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[PerformedByPickerViewModel] DB observe failed")
                    flowOf(PerformedByPickerUiState(isLoading = false, error = e.message))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PerformedByPickerUiState())

    init {
        refreshMembers()
    }

    fun onQueryChanged(q: String) {
        _query.value = q
    }

    fun onFreeTextChanged(name: String) {
        _freeText.value = name
    }

    fun refreshMembers() {
        val accountId = identity.getActiveAccountId() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val members = serviceClient.getMembers(accountId)
                val db = dbFactory.get(accountId)
                val now = System.currentTimeMillis()
                val entities = members.map { it.toEntity(accountId, now) }
                db.userDao().upsertAll(entities)
                Timber.d("[PerformedByPickerViewModel] Refreshed ${members.size} members")
            } catch (e: Exception) {
                Timber.e(e, "[PerformedByPickerViewModel] refreshMembers failed")
                _error.value = "Could not refresh members"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun UserResponse.toEntity(accountId: String, now: Long) = UserEntity(
        userId = user_id,
        accountId = accountId,
        displayName = display_name,
        email = email,
        photoUrl = photo_url,
        role = role,
        isActive = true,
        createdAt = now,
        updatedAt = now,
        serverVersion = 0L,
        seq = null,
    )
}
