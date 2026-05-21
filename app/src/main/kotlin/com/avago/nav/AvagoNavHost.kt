package com.avago.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.avago.feature.auth.SignInScreen
import com.avago.feature.assets.AssetsListScreen

@Composable
fun AvagoNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "sign_in") {
        composable("sign_in") {
            SignInScreen(onSignedIn = { navController.navigate("assets") {
                popUpTo("sign_in") { inclusive = true }
            }})
        }
        composable("assets") {
            AssetsListScreen()
        }
    }
}
