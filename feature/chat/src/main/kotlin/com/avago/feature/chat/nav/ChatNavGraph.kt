package com.avago.feature.chat.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.avago.feature.chat.ChatListScreen
import com.avago.feature.chat.ThreadScreen

sealed class ChatRoute(val route: String) {
    object List : ChatRoute("chat/list")
    object Thread : ChatRoute("chat/thread/{threadId}") {
        fun createRoute(threadId: String) = "chat/thread/$threadId"
    }
}

fun NavGraphBuilder.chatNavGraph(navController: NavHostController) {
    composable(route = ChatRoute.List.route) {
        ChatListScreen(
            onThreadClick = { threadId ->
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
        )
    }
}
