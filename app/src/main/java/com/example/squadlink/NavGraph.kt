package com.example.squadlink.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.squadlink.ui.screens.GameMasterScreen
import com.example.squadlink.ui.screens.HomeScreen
import com.example.squadlink.ui.screens.JoinGameScreen
import com.example.squadlink.ui.screens.MapScreen
import com.example.squadlink.ui.screens.ProfileScreen
import com.example.squadlink.ui.screens.SquadScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onJoinGame = { navController.navigate(Screen.JoinGame.route) },
                onCreateGame = { navController.navigate(Screen.GameMaster.route) }
            )
        }

        composable(Screen.JoinGame.route) {
            JoinGameScreen(
                onGameJoined = {
                    navController.navigate(Screen.Map.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Map.route) {
            MapScreen()
        }

        composable(Screen.Squad.route) {
            SquadScreen()
        }

        composable(Screen.Profile.route) {
            ProfileScreen()
        }

        composable(Screen.GameMaster.route) {
            GameMasterScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}