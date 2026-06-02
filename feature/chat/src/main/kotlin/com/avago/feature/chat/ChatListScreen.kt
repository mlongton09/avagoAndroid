package com.avago.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Notifications

import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.ChatThreadEntity
import com.avago.core.network.model.CustomSection
import com.avago.feature.assets.model.AssetTypes
import com.avago.feature.chat.ui.assetTypeKey
import com.avago.feature.chat.ui.displayTitle
import com.avago.feature.chat.ui.iconEmoji
import com.avago.feature.chat.ui.lastMessagePreviewText
import com.avago.feature.chat.ui.relativeTimestamp
import com.avago.feature.chat.viewmodel.ChatListViewModel
import com.avago.core.ui.AvagoSearchBar
import com.avago.feature.chat.viewmodel.ThreadFilter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    onThreadClick: (threadId: String) -> Unit,
    onNewThread: (tab: Int) -> Unit,
    onNewAssetThread: () -> Unit = {},
    onMentions: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    pickedAssetId: String? = null,
    onPickedAssetHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // When the asset picker returns an asset, resolve & open its chat thread.
    LaunchedEffect(pickedAssetId) {
        pickedAssetId?.let { assetId ->
            viewModel.openAssetThread(assetId) { threadId -> onThreadClick(threadId) }
            onPickedAssetHandled()
        }
    }
    var showNewSectionDialog by rememberSaveable { mutableStateOf(false) }
    var newSectionName by rememberSaveable { mutableStateOf("") }
    var sectionMenuThread by remember { mutableStateOf<ChatThreadEntity?>(null) }
    var sectionMenuSection by remember { mutableStateOf<CustomSection?>(null) }
    var renameSection by remember { mutableStateOf<CustomSection?>(null) }
    var renameSectionName by rememberSaveable { mutableStateOf("") }

    val filterLabels = listOf(
        ThreadFilter.ALL to stringResource(R.string.chat_filter_all),
        ThreadFilter.DIRECT to stringResource(R.string.chat_filter_direct),
        ThreadFilter.WORK_ORDERS to stringResource(R.string.chat_filter_work_orders),
        ThreadFilter.ASSETS to stringResource(R.string.chat_filter_assets),
    )

    if (showNewSectionDialog) {
        AlertDialog(
            onDismissRequest = { showNewSectionDialog = false },
            title = { Text("New Section") },
            text = {
                OutlinedTextField(
                    value = newSectionName,
                    onValueChange = { newSectionName = it },
                    label = { Text("Section name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createCustomSection(newSectionName)
                        newSectionName = ""
                        showNewSectionDialog = false
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewSectionDialog = false }) { Text("Cancel") }
            },
        )
    }

    sectionMenuThread?.let { thread ->
        AlertDialog(
            onDismissRequest = { sectionMenuThread = null },
            title = { Text("Move to Section") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (uiState.customSections.isEmpty()) {
                        Text("No sections — create one with +")
                    } else {
                        uiState.customSections.forEach { section ->
                            val isInSection = thread.threadId in section.threadIds
                            TextButton(
                                onClick = {
                                    viewModel.toggleThreadInSection(thread.threadId, section.id)
                                    sectionMenuThread = null
                                },
                            ) {
                                Text(if (isInSection) "Remove from ${section.name}" else "Add to ${section.name}")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sectionMenuThread = null }) { Text("Close") }
            },
        )
    }

    sectionMenuSection?.let { section ->
        AlertDialog(
            onDismissRequest = { sectionMenuSection = null },
            title = { Text(section.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            renameSection = section
                            renameSectionName = section.name
                            sectionMenuSection = null
                        },
                    ) { Text("Rename") }
                    TextButton(
                        onClick = {
                            viewModel.deleteCustomSection(section.id)
                            sectionMenuSection = null
                        },
                    ) { Text("Delete") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sectionMenuSection = null }) { Text("Cancel") }
            },
        )
    }

    renameSection?.let { section ->
        AlertDialog(
            onDismissRequest = { renameSection = null },
            title = { Text("Rename Section") },
            text = {
                OutlinedTextField(
                    value = renameSectionName,
                    onValueChange = { renameSectionName = it },
                    label = { Text("Section name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameCustomSection(section.id, renameSectionName)
                        renameSection = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameSection = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        // No top bar here: MainScaffold already renders the "Chat" heading
        // (brand icon + title). A second app bar just stacked a ~half-inch
        // empty band above the list. The settings gear that lived here is
        // relocated into the filter row below.
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
                // Search bar — mirrors iOS ThreadListViewController which has no
                // bell shortcut in the search row (Mentions is exposed as the
                // first row of the list via MentionsShortcutRow below).
                AvagoSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    placeholder = "Filter by person or asset name",
                    modifier = Modifier
                        .fillMaxWidth()
                        // Tight to the "Chat" app bar — no dead band above the
                        // thread list (iOS keeps search in the nav bar).
                        .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
                )

                // Filter row — Unread toggle + New Section, with the chat
                // settings gear trailing (relocated from the removed app bar).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.unreadOnly,
                        onClick = { viewModel.setUnreadOnly(!uiState.unreadOnly) },
                        label = { Text(stringResource(R.string.chat_filter_unread)) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { showNewSectionDialog = true },
                        label = { Text("+ New Section") },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Chat settings")
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    if (uiState.syncError != null && uiState.threads.isEmpty()) {
                        item(key = "sync_error") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Sync failed: ${uiState.syncError}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                        item(key = "mentions_shortcut") {
                            MentionsShortcutRow(
                                unreadMentionCount = uiState.unreadMentionCount,
                                onClick = onMentions,
                            )
                        }
                        item(key = "team_shortcut") {
                            TeamRoomShortcutRow(
                                onClick = {
                                    val tid = uiState.teamThreadId
                                    if (tid != null) {
                                        onThreadClick(tid)
                                    } else {
                                        // Lazy-load then navigate when ready.
                                        viewModel.openTeamThread(onThreadClick)
                                    }
                                },
                            )
                        }
                        // Group threads by type and custom sections — mirrors iOS ThreadListViewController sections.
                        val assignedToCustom = uiState.customSections.flatMap { it.threadIds }.toSet()
                        val defaultThreads = uiState.threads.filter { it.threadId !in assignedToCustom }
                        val assetWoThreads = defaultThreads.filter {
                            (it.threadType == "wo" || it.threadType == "asset") && it.isFavorite
                        }
                        val directThreads = defaultThreads.filter { it.threadType == "direct" && it.isFavorite }
                        val groupThreads = defaultThreads.filter { it.threadType == "group" && it.isFavorite }

                        // Sections render their headers unconditionally (even when
                        // empty) so each thread type's "+" affordance is always
                        // reachable — mirrors iOS ThreadListViewController, where
                        // the Favorite Assets / Direct Messages / Group Chats
                        // headers (and their + buttons) are always present.

                        // "Favorite Assets" — "+" opens the asset picker.
                        stickyHeader(key = "header_assets") {
                            SectionHeader("Favorite Assets", onAdd = onNewAssetThread)
                        }
                        items(assetWoThreads, key = { it.threadId }) { thread ->
                            ThreadRowWithSwipe(thread, viewModel, onThreadClick) { sectionMenuThread = thread }
                        }

                        // "Direct Messages" — "+" opens New Thread on the Direct tab.
                        stickyHeader(key = "header_direct") {
                            SectionHeader("Direct Messages", onAdd = { onNewThread(0) })
                        }
                        items(directThreads, key = { it.threadId }) { thread ->
                            ThreadRowWithSwipe(thread, viewModel, onThreadClick) { sectionMenuThread = thread }
                        }

                        // "Group Chats" — "+" opens New Thread on the Group tab.
                        stickyHeader(key = "header_groups") {
                            SectionHeader("Group Chats", onAdd = { onNewThread(1) })
                        }
                        items(groupThreads, key = { it.threadId }) { thread ->
                            ThreadRowWithSwipe(thread, viewModel, onThreadClick) { sectionMenuThread = thread }
                        }

                        uiState.customSections.forEach { section ->
                            val sectionThreads = section.threadsFrom(uiState.threads)
                            stickyHeader(key = "header_custom_${section.id}") {
                                SectionHeader(
                                    title = section.name,
                                    onLongClick = { sectionMenuSection = section },
                                )
                            }
                            items(sectionThreads, key = { it.threadId }) { thread ->
                                ThreadRowWithSwipe(thread, viewModel, onThreadClick) { sectionMenuThread = thread }
                            }
                        }

                    }
            }
        }
        // New-conversation FAB — defaults to the Direct tab.
        FloatingActionButton(
            onClick = { onNewThread(0) },
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
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = "Mentions",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "Mentions",
            // iOS MentionsShortcutCell uses bodyBoldFont (17pt semibold) →
            // titleLarge in AvagoTypography.
            style = MaterialTheme.typography.titleLarge,
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
            // iOS TeamRoomCell uses SF Symbol "megaphone.fill" at 28pt in
            // accentBlue. Campaign is Material's filled-megaphone equivalent;
            // colorScheme.primary == accentBlue (0969da/539bf5); 28dp matches.
            Icons.Default.Campaign,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "Team Room",
            // iOS TeamRoomCell uses bodyBoldFont (17pt semibold) → titleLarge.
            style = MaterialTheme.typography.titleLarge,
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
    onSectionMenu: () -> Unit,
) {
    val swipeState = rememberSwipeToDismissBoxState(
        // Always reject the dismiss — actions fire from LaunchedEffect below so we
        // can reliably snap the row back to Settled.
        confirmValueChange = { false },
    )
    LaunchedEffect(swipeState.currentValue) {
        when (swipeState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                viewModel.setFavorite(thread.threadId, !thread.isFavorite)
                swipeState.reset()
            }
            else -> Unit
        }
    }
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = if (thread.isFavorite) "Unfavorite" else "Favorite",
                    tint = if (thread.isFavorite) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) {
        ThreadRow(
            thread = thread,
            onClick = { onThreadClick(thread.threadId) },
            onLongClick = onSectionMenu,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
) {
    val rowModifier = if (onLongClick == null) {
        modifier
    } else {
        modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
    }
    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        )
        // Trailing "+" to create a thread of this type — iOS parity
        // (ThreadListViewController makeSectionHeader plus button).
        if (onAdd != null) {
            IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add to $title",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ThreadRow(
    thread: ChatThreadEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasUnread = thread.unreadCount > 0

    // Opaque background so the swipe-to-favorite Star layer (primaryContainer,
    // a light blue) only shows while the row is actively swiped — not bleeding
    // through a transparent row at rest. iOS ThreadRowCell is likewise a solid
    // bg1() fill for every thread type (asset rows included); only the avatar
    // circle is colored. The trailing hairline divider mirrors iOS's table
    // separatorColor() — a very light grey line between rows.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon — asset threads get the colored AvatarView treatment from iOS
        // (ThreadRowCell renders the same colored circle + asset glyph used in
        // the Assets list). Every other thread type uses an emoji.
        val assetKey = thread.assetTypeKey()
        if (assetKey != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(AssetTypes.colorHexFor(assetKey)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(AssetTypes.iconResFor(assetKey)),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Text(
                text = thread.iconEmoji() ?: "💬",
                fontSize = 24.sp,
                modifier = Modifier.size(36.dp),
            )
        }

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
                    // iOS ThreadRowCell: bodyBoldFont (17 semi) when unread,
                    // bodyFont (17 reg) otherwise. titleLarge in AvagoTypography
                    // is the 17/semibold slot; bodyLarge is the 17/regular slot.
                    style = if (hasUnread) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = thread.relativeTimestamp(),
                    // iOS uses smallFont (13 reg) → bodyMedium.
                    style = MaterialTheme.typography.bodyMedium,
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
                    // iOS previewLabel uses smallFont (13 reg) → bodyMedium.
                    style = MaterialTheme.typography.bodyMedium,
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
        HorizontalDivider(
            // iOS uses the default UITableView separator: a very light grey line
            // inset from the leading edge. onSurface @ 0.15 alpha matches the
            // grey used by DateSeparatorItem elsewhere in chat.
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        )
    }
}

private fun CustomSection.threadsFrom(threads: List<ChatThreadEntity>): List<ChatThreadEntity> {
    val byId = threads.associateBy { it.threadId }
    return threadIds.mapNotNull { byId[it] }.filter { it.isFavorite }
}
