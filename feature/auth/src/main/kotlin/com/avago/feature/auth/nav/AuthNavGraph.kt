package com.avago.feature.auth.nav

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.avago.feature.auth.AccountSwitcherScreen
import com.avago.feature.auth.EmailSignInScreen
import com.avago.feature.auth.SignInScreen

object AuthRoute {
    const val GRAPH = "auth_graph"
    const val SignIn = "sign_in"
    const val EmailSignIn = "sign_in/email"
    const val AccountSwitcher = "account_switcher"
}

fun NavGraphBuilder.authNavGraph(
    navController: NavController,
    onSignedIn: () -> Unit,
) {
    navigation(
        startDestination = AuthRoute.SignIn,
        route = AuthRoute.GRAPH,
    ) {
        composable(AuthRoute.SignIn) {
            SignInScreen(
                onSignedIn = onSignedIn,
                onNavigateToEmail = { navController.navigate(AuthRoute.EmailSignIn) },
            )
        }

        composable(AuthRoute.EmailSignIn) {
            EmailSignInScreen(
                onBack = { navController.popBackStack() },
                onSignedIn = onSignedIn,
            )
        }

        composable(AuthRoute.AccountSwitcher) {
            AccountSwitcherScreen(
                onDismiss = { navController.popBackStack() },
                onAddAccount = {
                    navController.navigate(AuthRoute.SignIn) {
                        popUpTo(AuthRoute.AccountSwitcher) { inclusive = true }
                    }
                },
            )
        }
    }
}
