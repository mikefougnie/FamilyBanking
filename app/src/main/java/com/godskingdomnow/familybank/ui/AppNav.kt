package com.godskingdomnow.familybank.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.godskingdomnow.familybank.AppContainer

@Composable
fun AppNav(app: AppContainer) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(app, openChild = { nav.navigate("child/$it") }, openAccount = { nav.navigate("account") })
        }
        composable("child/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            ChildScreen(app, id, back = { nav.popBackStack("home", inclusive = false) })
        }
        composable("account") {
            AccountScreen(app, back = { nav.popBackStack() })
        }
    }
}
