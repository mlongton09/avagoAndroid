package com.avago.feature.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.feature.chat.data.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MentionsViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val identity: IdentityManager,
) : ViewModel() {

    private val _mentions = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val mentions: StateFlow<List<ChatMessageEntity>> = _mentions

    init {
        observeMentions()
    }

    private fun observeMentions() {
        val username = identity.getActiveUserId() ?: return
        viewModelScope.launch {
            repository.observeMentions(username)
                .catch { e -> Timber.e(e, "observeMentions error") }
                .collect { _mentions.value = it }
        }
    }
}
