package com.avago.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Badge
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.feature.chat.ui.displayTitle
import com.avago.feature.chat.ui.iconEmoji
import com.avago.feature.chat.ui.lastMessagePreviewText
import com.avago.feature.chat.ui.relativeTimestamp
import com.avago.feature.chat.viewmodel.ChatListViewModel
import com.avago.core.ui.AvagoSearchBar
import com.avago.feature.chat.viewmodel.ThreadFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onThreadClick: (threadId: String) -> Unit,
    onNewThread: () -> Unit,
    onMentions: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val filterLabels = listOf(
        ThreadFilter.ALL to "All",
        ThreadFilter.DIRECT to "Direct",
        ThreadFilter.WORK_ORDERS to "Work Orders",
        ThreadFilter.ASSETS to "Assets",
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search + notifications row — mirrors iOS: search bar with bell shortcut
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvagoSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        placeholder = "Filter by person or asset name",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    )
                    IconButton(
                        onClick = onMentions,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Mentions")
                    }
                }

                // Filter chips — All / Direct / Work Orders / Assets + Unread toggle
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filterLabels) { (filter, label) ->
                        FilterChip(
                            selected = uiState.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(label) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.unreadOnly,
                            onClick = { viewModel.setUnreadOnly(!uiState.unreadOnly) },
                            label = { Text("Unread") },
                        )
                    }
                }

                // Thread list
                if (uiState.threads.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.syncError != null) {
                            Text(
                                text = "Sync failed: ${uiState.syncError}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        } else if (!uiState.isRefreshing) {
                            Text(
                                text = "No conversations yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        item(key = "mentions_shortcut") {
                            MentionsShortcutRow(
                                unreadMentionCount = uiState.unreadMentionCount,
                                onClick = onMentions,
                            )
                        }
                        if (uiState.teamThreadId != null) {
                            item(key = "team_shortcut") {
                                TeamRoomShortcutRow(onClick = { onThreadClick(uiState.teamThreadId!!) })
                            }
                        }
                        // Group threads by type — mirrors iOS ThreadListViewController sections
                        val teamThreads = uiState.threads.filter { it.threadType == "team" }
                        val assetWoThreads = uiState.threads.filter { it.threadType == "wo" || it.threadType == "asset" }
                        val directThreads = uiState.threads.filter { it.threadType == "direct" }
                        val groupThreads = uiState.threads.filter { it.threadType == "group" }
                        val otherThreads = uiState.threads.filter {
                            it.threadType !in setOf("team", "wo", "asset", "direct", "group")
                        }

                        // Team threads — no section header (top special rows, like iOS)
                        items(teamThreads, key = { it.threadId }) { thread ->
                            ThreadRowWithSwipe(thread, viewModel, onThreadClick)
                        }

                        // "Work Orders & Assets" section
                        if (assetWoThreads.isNotEmpty()) {
                            stickyHeader(key = "header_assets") {
                                SectionHeader("Work Orders & Assets")
                            }
                            items(assetWoThreads, key = { it.threadId }) { thread ->
                                ThreadRowWithSwipe(thread, viewModel, onThreadClick)
                            }
                        }

                        // "Direct Messages" section
                        if (directThreads.isNotEmpty()) {
                            stickyHeader(key = "header_direct") {
                                SectionHeader("Direct Messages")
                            }
                            items(directThreads, key = { it.threadId }) { thread ->
                                ThreadRowWithSwipe(thread, viewModel, onThreadClick)
                            }
                        }

                        // "Group Chats" section
                        if (groupThreads.isNotEmpty()) {
                            stickyHeader(key = "header_groups") {
                                SectionHeader("Group Chats")
                            }
                            items(groupThreads, key = { it.threadId }) { thread ->
                                ThreadRowWithSwipe(thread, viewModel, onThreadClick)
                            }
                        }

                        // "Other" section
                        if (otherThreads.isNotEmpty()) {
                            stickyHeader(key = "header_other") {
                                SectionHeader("Other")
                            }
                            items(otherThreads, key = { it.threadId }) { thread ->
                                ThreadRowWithSwipe(thread, viewModel, onThreadClick)
                            }
                        }
                    }
                }
            }
        }
        // New-conversation FAB
        FloatingActionButton(
            onClick = onNewThread,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
        ) {
            Icon(Icons.Default.Add, contentDescription = "New conversation")
        }
        } // Box
    }
}

@Composable
private fun MentionsShortcutRow(
    unreadMentionCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bell icon as emoji
        Text(
            text = "🔔", // 🔔
            fontSize = 24.sp,
            modifier = Modifier.size(36.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "Mentions",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )

        if (unreadMentionCount > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ) {
                Text(text = if (unreadMentionCount > 99) "99+" else unreadMentionCount.toString())
            }
        }
    }
}

@Composable
private fun TeamRoomShortcutRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Team Room",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Thread row wrapped in swipe-to-favorite / swipe-to-mute. Extracted so it can be
 * reused across all section groups in the LazyColumn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadRowWithSwipe(
    thread: ChatThreadEntity,
    viewModel: ChatListViewModel,
    onThreadClick: (threadId: String) -> Unit,
) {
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    viewModel.setFavorite(thread.threadId, !thread.isFavorite)
                    false // don't dismiss, just toggle
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    viewModel.muteThread(thread.threadId, 8)
                    false
                }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            val dir = swipeState.targetValue
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (dir == SwipeToDismissBoxValue.StartToEnd)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (dir == SwipeToDismissBoxValue.StartToEnd)
                    Arrangement.Start else Arrangement.End,
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = if (thread.isFavorite) "Unfavorite" else "Favorite",
                        tint = if (thread.isFavorite) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Mute 8 hours",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
    ) {
        ThreadRow(
            thread = thread,
            onClick = { onThreadClick(thread.threadId) },
        )
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun ThreadRow(
    thread: ChatThreadEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasUnread = thread.unreadCount > 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        Text(
            text = thread.iconEmoji() ?: "💬",
            fontSize = 24.sp,
            modifier = Modifier.size(36.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title + preview
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = thread.displayTitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = thread.relativeTimestamp(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = thread.lastMessagePreviewText().ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (hasUnread) 0.87f else 0.55f,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (hasUnread) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ) {
                        Text(text = if (thread.unreadCount > 99) "99+" else thread.unreadCount.toString())
                    }
                }
            }
        }
    }
}
