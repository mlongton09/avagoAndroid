package com.avago.feature.chat

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.avago.feature.chat.nav.ChatRoute
import com.avago.feature.chat.nav.chatNavGraph

@Composable
fun ChatRootScreen() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ChatRoute.List.route,
    ) {
        chatNavGraph(navController = navController)
    }
}
