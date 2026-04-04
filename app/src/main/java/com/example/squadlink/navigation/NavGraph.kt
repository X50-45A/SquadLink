package com.example.squadlink.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.squadlink.data.UserPreferencesRepository
import com.example.squadlink.ui.screens.GameMasterScreen
import com.example.squadlink.ui.screens.HomeScreen
import com.example.squadlink.ui.screens.JoinGameScreen
import com.example.squadlink.ui.screens.LoginScreen
import com.example.squadlink.ui.map.MapScreen
import com.example.squadlink.ui.map.FieldSelectionViewModel
import com.example.squadlink.ui.session.GameSessionViewModel
import com.example.squadlink.ui.screens.ProfileScreen
import com.example.squadlink.ui.screens.SquadScreen
import com.example.squadlink.ui.settings.SettingsScreen
import com.example.squadlink.ui.settings.SettingsViewModel
import com.example.squadlink.ui.profile.ProfileViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            val context = LocalContext.current
            val vm: ProfileViewModel = viewModel(
                factory = ProfileViewModel.Factory(UserPreferencesRepository(context))
            )
            LoginScreen(
                vm = vm,
                onLoggedIn = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val context = LocalContext.current
            val sessionVm: GameSessionViewModel = viewModel(
                factory = GameSessionViewModel.Factory(UserPreferencesRepository(context))
            )
            HomeScreen(
                sessionVm = sessionVm,
                onJoinGame = { navController.navigate(Screen.JoinGame.route) },
                onCreateGame = { navController.navigate(Screen.GameMaster.route) }
            )
        }

        composable(Screen.JoinGame.route) {
            val context = LocalContext.current
            val fieldVm: FieldSelectionViewModel = viewModel(
                factory = FieldSelectionViewModel.Factory(UserPreferencesRepository(context))
            )
            val sessionVm: GameSessionViewModel = viewModel(
                factory = GameSessionViewModel.Factory(UserPreferencesRepository(context))
            )
            JoinGameScreen(
                fieldVm = fieldVm,
                sessionVm = sessionVm,
                onGameJoined = {
                    navController.navigate(Screen.Map.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Map.route) {
            val context = LocalContext.current
            val fieldVm: FieldSelectionViewModel = viewModel(
                factory = FieldSelectionViewModel.Factory(UserPreferencesRepository(context))
            )
            val sessionVm: GameSessionViewModel = viewModel(
                factory = GameSessionViewModel.Factory(UserPreferencesRepository(context))
            )
            MapScreen(fieldVm = fieldVm, sessionVm = sessionVm)
        }

        composable(Screen.Squad.route) {
            val context = LocalContext.current
            val sessionVm: GameSessionViewModel = viewModel(
                factory = GameSessionViewModel.Factory(UserPreferencesRepository(context))
            )
            SquadScreen(sessionVm = sessionVm)
        }

        composable(Screen.Profile.route) {
            val context = LocalContext.current
            val vm: ProfileViewModel = viewModel(
                factory = ProfileViewModel.Factory(UserPreferencesRepository(context))
            )
            ProfileScreen(
                vm = vm,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            val context = LocalContext.current
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(UserPreferencesRepository(context))
            )
            SettingsScreen(
                vm = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.GameMaster.route) {
            val context = LocalContext.current
            val fieldVm: FieldSelectionViewModel = viewModel(
                factory = FieldSelectionViewModel.Factory(UserPreferencesRepository(context))
            )
            val sessionVm: GameSessionViewModel = viewModel(
                factory = GameSessionViewModel.Factory(UserPreferencesRepository(context))
            )
            GameMasterScreen(
                fieldVm = fieldVm,
                sessionVm = sessionVm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
