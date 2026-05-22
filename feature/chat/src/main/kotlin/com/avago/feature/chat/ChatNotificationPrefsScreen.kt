package com.avago.feature.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.ChatPrefsRequest
import com.avago.core.network.model.ChatPrefsResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class ChatNotificationPrefsViewModel @Inject constructor(
    private val serviceClient: AvagoServiceClient,
) : ViewModel() {

    private val _prefs = MutableStateFlow(ChatPrefsResponse())
    val prefs: StateFlow<ChatPrefsResponse> = _prefs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = serviceClient.getChatPrefs()) {
                is NetworkResult.Success -> _prefs.value = r.data
                is NetworkResult.Error -> Timber.w("getChatPrefs failed: ${r.message}")
                is NetworkResult.Unauthorized -> Timber.w("getChatPrefs unauthorized")
            }
            _isLoading.value = false
        }
    }

    fun save(update: ChatPrefsRequest) {
        viewModelScope.launch {
            when (val r = serviceClient.putChatPrefs(update)) {
                is NetworkResult.Success -> _prefs.value = r.data
                is NetworkResult.Error -> Timber.w("putChatPrefs failed: ${r.message}")
                is NetworkResult.Unauthorized -> Timber.w("putChatPrefs unauthorized")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatNotificationPrefsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatNotificationPrefsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Notification Preferences") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            item {
                Text(
                    text = "General",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                )
            }
            item {
                PrefToggleItem(
                    title = "Notification Sound",
                    checked = prefs.notification_sound,
                    onCheckedChange = { viewModel.save(ChatPrefsRequest(notification_sound = it)) },
                )
            }
            item {
                PrefToggleItem(
                    title = "Show Previews",
                    supporting = "Show message content in notifications",
                    checked = prefs.show_previews,
                    onCheckedChange = { viewModel.save(ChatPrefsRequest(show_previews = it)) },
                )
            }
            item {
                PrefToggleItem(
                    title = "Badge Count",
                    supporting = "Show unread count on app icon",
                    checked = prefs.badge_count,
                    onCheckedChange = { viewModel.save(ChatPrefsRequest(badge_count = it)) },
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(
                    text = "Push Notifications By Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                )
            }
            item {
                PrefToggleItem(
                    title = "Mentions",
                    supporting = "Notify when someone @mentions you",
                    checked = prefs.mention_push_enabled,
                    onCheckedChange = { viewModel.save(ChatPrefsRequest(mention_push_enabled = it)) },
                )
            }
            item {
                PrefToggleItem(
                    title = "Broadcasts",
                    supporting = "Notify for broadcast messages",
                    checked = prefs.broadcast_push_enabled,
                    onCheckedChange = { viewModel.save(ChatPrefsRequest(broadcast_push_enabled = it)) },
                )
            }
            item {
                PrefToggleItem(
                    title = "Work Order Updates",
                    supporting = "Notify when a work order thread is updated",
                    checked = prefs.wo_push_enabled,
                    onCheckedChange = { viewModel.save(ChatPrefsRequest(wo_push_enabled = it)) },
                )
            }
            item {
                PrefToggleItem(
                    title = "Team Room Messages",
                    supporting = "Notify for messages in team rooms",
                    checked = prefs.team_room_push_enabled,
                    onCheckedChange = { viewModel.save(ChatPrefsRequest(team_room_push_enabled = it)) },
                )
            }
            item {
                PrefToggleItem(
                    title = "Reactions to Your Messages",
                    supporting = "Notify when someone reacts to your message",
                    checked = prefs.reaction_to_you_push_enabled,
                    onCheckedChange = { viewModel.save(ChatPrefsRequest(reaction_to_you_push_enabled = it)) },
                )
            }
        }
    }
}

@Composable
private fun PrefToggleItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = modifier,
    )
}
