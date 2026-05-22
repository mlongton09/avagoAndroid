package com.avago.feature.chat.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.avago.feature.chat.ChatListScreen
import com.avago.feature.chat.ChatSettingsScreen
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
    object NewThread : ChatRoute("chat/new-thread")
    object ThreadMembers : ChatRoute("chat/thread/{threadId}/members") {
        fun createRoute(threadId: String) = "chat/thread/$threadId/members"
    }
    object ChatSettings : ChatRoute("chat/thread/{threadId}/settings") {
        fun createRoute(threadId: String) = "chat/thread/$threadId/settings"
    }
    object MediaGallery : ChatRoute("chat/thread/{threadId}/media") {
        fun createRoute(threadId: String) = "chat/thread/$threadId/media"
    }
    object Subthread : ChatRoute("chat/thread/{threadId}/reply/{messageId}") {
        fun createRoute(threadId: String, messageId: String) =
            "chat/thread/$threadId/reply/$messageId"
    }
}

fun NavGraphBuilder.chatNavGraph(navController: NavHostController) {
    composable(route = ChatRoute.List.route) {
        ChatListScreen(
            onThreadClick = { threadId ->
                navController.navigate(ChatRoute.Thread.createRoute(threadId))
            },
            onNewThread = { navController.navigate(ChatRoute.NewThread.route) },
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
        )
    }

    composable(ChatRoute.NewThread.route) {
        NewThreadScreen(
            onBack = { navController.popBackStack() },
            onThreadCreated = { threadId ->
                navController.navigate(ChatRoute.Thread.createRoute(threadId)) {
                    popUpTo(ChatRoute.NewThread.route) { inclusive = true }
                }
            },
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
        ChatSettingsScreen(onBack = { navController.popBackStack() })
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
