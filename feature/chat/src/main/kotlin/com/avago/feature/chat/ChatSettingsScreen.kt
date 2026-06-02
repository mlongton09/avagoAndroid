package com.avago.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.db.ChatDatabaseFactory
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import com.avago.core.network.model.ChatPrefsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class ChatPrefsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val mentionPushEnabled: Boolean = true,
    val broadcastPushEnabled: Boolean = true,
    val woPushEnabled: Boolean = true,
    val teamRoomPushEnabled: Boolean = true,
    val reactionToYouPushEnabled: Boolean = true,
    val notificationSound: Boolean = true,
    val showPreviews: Boolean = true,
    val showBroadcastsInline: Boolean = true,
    val cacheCleared: Boolean = false,
    val quietHoursStart: String? = null,
    val quietHoursEnd: String? = null,
    val quietHoursTimezone: String? = null,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class ChatSettingsViewModel @Inject constructor(
    private val serviceClient: AvagoServiceClient,
    private val identity: IdentityManager,
    private val chatDbFactory: ChatDatabaseFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatPrefsUiState())
    val state: StateFlow<ChatPrefsUiState> = _state.asStateFlow()

    init {
        loadPrefs()
    }

    private fun loadPrefs() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = serviceClient.getChatPrefs()) {
                is NetworkResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        mentionPushEnabled = result.data.mention_push_enabled,
                        broadcastPushEnabled = result.data.broadcast_push_enabled,
                        woPushEnabled = result.data.wo_push_enabled,
                        teamRoomPushEnabled = result.data.team_room_push_enabled,
                        reactionToYouPushEnabled = result.data.reaction_to_you_push_enabled,
                        notificationSound = result.data.notification_sound,
                        showPreviews = result.data.show_previews,
                        showBroadcastsInline = result.data.show_broadcasts_inline,
                        quietHoursStart = result.data.quiet_hours_start,
                        quietHoursEnd = result.data.quiet_hours_end,
                        quietHoursTimezone = result.data.quiet_hours_timezone,
                    )
                }
                is NetworkResult.Error -> _state.update { it.copy(loading = false, error = result.message) }
                NetworkResult.Unauthorized -> _state.update { it.copy(loading = false, error = "Unauthorized") }
            }
        }
    }

    fun setMentionPush(v: Boolean) =
        updatePref({ it.copy(mentionPushEnabled = v) }) { ChatPrefsRequest(mention_push_enabled = v) }

    fun setBroadcastPush(v: Boolean) =
        updatePref({ it.copy(broadcastPushEnabled = v) }) { ChatPrefsRequest(broadcast_push_enabled = v) }

    fun setWoPush(v: Boolean) =
        updatePref({ it.copy(woPushEnabled = v) }) { ChatPrefsRequest(wo_push_enabled = v) }

    fun setTeamRoomPush(v: Boolean) =
        updatePref({ it.copy(teamRoomPushEnabled = v) }) { ChatPrefsRequest(team_room_push_enabled = v) }

    fun setReactionToYouPush(v: Boolean) =
        updatePref({ it.copy(reactionToYouPushEnabled = v) }) { ChatPrefsRequest(reaction_to_you_push_enabled = v) }

    fun setNotificationSound(v: Boolean) =
        updatePref({ it.copy(notificationSound = v) }) { ChatPrefsRequest(notification_sound = v) }

    fun setShowPreviews(v: Boolean) =
        updatePref({ it.copy(showPreviews = v) }) { ChatPrefsRequest(show_previews = v) }

    fun setShowBroadcastsInline(v: Boolean) =
        updatePref({ it.copy(showBroadcastsInline = v) }) { ChatPrefsRequest(show_broadcasts_inline = v) }

    fun setQuietHours(start: String?, end: String?, timezone: String?) {
        _state.update { it.copy(quietHoursStart = start, quietHoursEnd = end, quietHoursTimezone = timezone) }
        viewModelScope.launch {
            when (val r = serviceClient.updateChatPrefs(
                ChatPrefsRequest(
                    quiet_hours_start = start,
                    quiet_hours_end = end,
                    quiet_hours_timezone = timezone,
                )
            )) {
                is NetworkResult.Error -> Timber.w("ChatSettingsViewModel: setQuietHours failed: ${r.message}")
                else -> {}
            }
        }
    }

    fun clearQuietHours() = setQuietHours(null, null, null)

    fun clearCache() {
        viewModelScope.launch {
            val accountId = identity.activeAccountId.value ?: return@launch
            chatDbFactory.close(accountId)
            _state.update { it.copy(cacheCleared = true) }
        }
    }

    fun consumeCacheCleared() {
        _state.update { it.copy(cacheCleared = false) }
    }

    private fun updatePref(
        localUpdate: (ChatPrefsUiState) -> ChatPrefsUiState,
        request: () -> ChatPrefsRequest,
    ) {
        _state.update(localUpdate)
        viewModelScope.launch {
            when (val r = serviceClient.updateChatPrefs(request())) {
                is NetworkResult.Error -> Timber.w("ChatSettingsViewModel: pref update failed: ${r.message}")
                else -> {}
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(
    threadId: String,
    onBack: () -> Unit,
    viewModel: ChatSettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    val cacheClearedMessage = stringResource(R.string.chat_settings_cache_cleared)

    LaunchedEffect(state.cacheCleared) {
        if (state.cacheCleared) {
            snackbarHostState.showSnackbar(cacheClearedMessage)
            viewModel.consumeCacheCleared()
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.chat_settings_clear_cache_title)) },
            text = { Text(stringResource(R.string.chat_settings_clear_cache_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    viewModel.clearCache()
                }) { Text(stringResource(R.string.chat_settings_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text(stringResource(R.string.chat_settings_cancel)) }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.chat_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_settings_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            item {
                SectionHeader(stringResource(R.string.chat_settings_quiet_hours))
            }
            item {
                QuietHoursRow(
                    start = state.quietHoursStart,
                    end = state.quietHoursEnd,
                    timezone = state.quietHoursTimezone,
                    onSet = { s, e, tz -> viewModel.setQuietHours(s, e, tz) },
                    onClear = viewModel::clearQuietHours,
                )
            }

            // ── Push Notifications ────────────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.chat_settings_push_notifications))
            }
            item {
                PrefToggleRow(
                    label = stringResource(R.string.chat_settings_specific_mentions),
                    checked = state.mentionPushEnabled,
                    onCheckedChange = viewModel::setMentionPush,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
            item {
                PrefToggleRow(
                    label = stringResource(R.string.chat_settings_broadcasts),
                    checked = state.broadcastPushEnabled,
                    onCheckedChange = viewModel::setBroadcastPush,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
            item {
                PrefToggleRow(
                    label = stringResource(R.string.chat_settings_wo_messages),
                    checked = state.woPushEnabled,
                    onCheckedChange = viewModel::setWoPush,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
            item {
                PrefToggleRow(
                    label = stringResource(R.string.chat_settings_team_room),
                    checked = state.teamRoomPushEnabled,
                    onCheckedChange = viewModel::setTeamRoomPush,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
            item {
                PrefToggleRow(
                    label = stringResource(R.string.chat_settings_reactions),
                    checked = state.reactionToYouPushEnabled,
                    onCheckedChange = viewModel::setReactionToYouPush,
                )
            }

            // ── Display ───────────────────────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.chat_settings_display))
            }
            item {
                PrefToggleRow(
                    label = stringResource(R.string.chat_settings_show_broadcasts_inline),
                    checked = state.showBroadcastsInline,
                    onCheckedChange = viewModel::setShowBroadcastsInline,
                )
            }

            // ── Cache ─────────────────────────────────────────────────────────
            item {
                SectionHeader(stringResource(R.string.chat_settings_cache))
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.chat_settings_clear_cache),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.clickable(role = Role.Button) {
                        showClearCacheDialog = true
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Private composables
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp, end = 16.dp),
    )
}

@Composable
private fun PrefToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = if (description != null) {
            { Text(description, style = MaterialTheme.typography.bodySmall) }
        } else null,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) },
    )
}

@Composable
private fun QuietHoursRow(
    start: String?,
    end: String?,
    timezone: String?,
    onSet: (String, String, String) -> Unit,
    onClear: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    val isEnabled = start != null && end != null
    val summary = if (isEnabled) "$start – $end${if (timezone != null) " ($timezone)" else ""}" else stringResource(R.string.chat_settings_off)

    ListItem(
        headlineContent = { Text(stringResource(R.string.chat_settings_quiet_hours)) },
        supportingContent = { Text(summary, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(
                checked = isEnabled,
                onCheckedChange = { on -> if (on) showDialog = true else onClear() },
            )
        },
        modifier = Modifier.clickable { if (!isEnabled) showDialog = true else onClear() },
    )

    if (showDialog) {
        QuietHoursDialog(
            initialStart = start ?: "22:00",
            initialEnd = end ?: "07:00",
            initialTimezone = timezone ?: java.util.TimeZone.getDefault().id,
            onConfirm = { s, e, tz -> onSet(s, e, tz); showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun QuietHoursDialog(
    initialStart: String,
    initialEnd: String,
    initialTimezone: String,
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var timezone by remember { mutableStateOf(initialTimezone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_settings_quiet_hours)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.chat_settings_quiet_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text(stringResource(R.string.chat_settings_quiet_start, "HH:mm")) },
                    placeholder = { Text("22:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it },
                    label = { Text(stringResource(R.string.chat_settings_quiet_end, "HH:mm")) },
                    placeholder = { Text("07:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = timezone,
                    onValueChange = { timezone = it },
                    label = { Text(stringResource(R.string.chat_settings_timezone)) },
                    placeholder = { Text("America/Chicago") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(start, end, timezone) },
                enabled = start.isNotBlank() && end.isNotBlank(),
            ) { Text(stringResource(R.string.chat_settings_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_settings_cancel)) }
        },
    )
}
