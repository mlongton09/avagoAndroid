package com.avago.feature.chat.nav

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.avago.feature.chat.ChatListScreen
import com.avago.feature.chat.ChatNotificationPrefsScreen
import com.avago.feature.chat.ChatSettingsScreen
import com.avago.feature.chat.MentionsScreen
import com.avago.feature.chat.NewThreadScreen
import com.avago.feature.chat.SubthreadScreen
import com.avago.feature.chat.ThreadMediaGalleryScreen
import com.avago.feature.chat.ThreadMembersScreen
import com.avago.feature.chat.ThreadScreen

sealed class ChatRoute(val route: String) {
    object List : ChatRoute("chat/list")
    object Thread : ChatRoute("chat/thread/{threadId}") {
        fun createRoute(threadId: String) = "chat/thread/$threadId"
    }
    object NewThread : ChatRoute("chat/new-thread?tab={tab}") {
        // tab: 0 = Direct, 1 = Group. Lets the Direct/Group section "+" headers
        // deep-link straight to the matching create tab (iOS parity).
        fun createRoute(tab: Int = 0) = "chat/new-thread?tab=$tab"
    }
    object Mentions : ChatRoute("chat/mentions")
    object ThreadMembers : ChatRoute("chat/thread/{threadId}/members") {
        fun createRoute(threadId: String) = "chat/thread/$threadId/members"
    }
    object ChatSettings : ChatRoute("chat/thread/{threadId}/settings") {
        fun createRoute(threadId: String) = "chat/thread/$threadId/settings"
    }
    object ChatNotificationPrefs : ChatRoute("chat/notification-prefs")
    object MediaGallery : ChatRoute("chat/thread/{threadId}/media") {
        fun createRoute(threadId: String) = "chat/thread/$threadId/media"
    }
    object Subthread : ChatRoute("chat/thread/{threadId}/reply/{messageId}") {
        fun createRoute(threadId: String, messageId: String) =
            "chat/thread/$threadId/reply/$messageId"
    }
}

fun NavGraphBuilder.chatNavGraph(navController: NavHostController) {
    composable(route = ChatRoute.List.route) { backStackEntry ->
        // Asset picker returns the chosen asset id via savedStateHandle
        // ("selected_asset_id" — set by the app-level onAssetPicked). When it
        // appears, ChatListScreen resolves & opens that asset's chat thread,
        // mirroring iOS's Favorite Assets "+" → asset picker flow.
        val pickedAssetId by backStackEntry.savedStateHandle
            .getStateFlow<String?>("selected_asset_id", null)
            .collectAsState()
        ChatListScreen(
            onThreadClick = { threadId ->
                navController.navigate(ChatRoute.Thread.createRoute(threadId))
            },
            onNewThread = { tab ->
                navController.navigate(ChatRoute.NewThread.createRoute(tab))
            },
            onNewAssetThread = { navController.navigate("assets/picker") },
            onMentions = { navController.navigate(ChatRoute.Mentions.route) },
            onOpenSettings = { navController.navigate(ChatRoute.ChatNotificationPrefs.route) },
            pickedAssetId = pickedAssetId,
            onPickedAssetHandled = {
                backStackEntry.savedStateHandle["selected_asset_id"] = null
            },
        )
    }

    composable(route = ChatRoute.Mentions.route) {
        MentionsScreen(
            onBack = { navController.popBackStack() },
            onOpenThread = { threadId ->
                navController.navigate(ChatRoute.Thread.createRoute(threadId))
            },
        )
    }

    composable(
        route = ChatRoute.Thread.route,
        arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val threadId = requireNotNull(backStackEntry.arguments?.getString("threadId"))
        ThreadScreen(
            threadId = threadId,
            onBack = { navController.popBackStack() },
            onMembers = { navController.navigate(ChatRoute.ThreadMembers.createRoute(threadId)) },
            onMedia = { navController.navigate(ChatRoute.MediaGallery.createRoute(threadId)) },
            onSettings = { navController.navigate(ChatRoute.ChatSettings.createRoute(threadId)) },
            onOpenSubthread = { messageId ->
                navController.navigate(ChatRoute.Subthread.createRoute(threadId, messageId))
            },
            onOpenWorkOrder = { woId ->
                navController.navigate("workorders/detail/$woId")
            },
        )
    }

    composable(
        route = ChatRoute.NewThread.route,
        arguments = listOf(navArgument("tab") {
            type = NavType.IntType
            defaultValue = 0
        }),
    ) { backStackEntry ->
        val tab = backStackEntry.arguments?.getInt("tab") ?: 0
        NewThreadScreen(
            onBack = { navController.popBackStack() },
            onThreadCreated = { threadId ->
                navController.navigate(ChatRoute.Thread.createRoute(threadId)) {
                    popUpTo(ChatRoute.NewThread.route) { inclusive = true }
                }
            },
            initialTab = tab,
        )
    }

    composable(
        route = ChatRoute.ThreadMembers.route,
        arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
    ) { back ->
        val threadId = back.arguments?.getString("threadId") ?: return@composable
        ThreadMembersScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = ChatRoute.ChatSettings.route,
        arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
    ) { back ->
        val threadId = back.arguments?.getString("threadId") ?: return@composable
        ChatSettingsScreen(
            threadId = threadId,
            onBack = { navController.popBackStack() },
        )
    }

    composable(ChatRoute.ChatNotificationPrefs.route) {
        ChatNotificationPrefsScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = ChatRoute.MediaGallery.route,
        arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
    ) { back ->
        val threadId = requireNotNull(back.arguments?.getString("threadId"))
        ThreadMediaGalleryScreen(
            threadId = threadId,
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = ChatRoute.Subthread.route,
        arguments = listOf(
            navArgument("threadId") { type = NavType.StringType },
            navArgument("messageId") { type = NavType.StringType },
        ),
    ) { back ->
        val threadId = requireNotNull(back.arguments?.getString("threadId"))
        val messageId = requireNotNull(back.arguments?.getString("messageId"))
        SubthreadScreen(
            threadId = threadId,
            messageId = messageId,
            onBack = { navController.popBackStack() },
        )
    }
}
