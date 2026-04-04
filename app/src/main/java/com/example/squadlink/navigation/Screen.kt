package com.example.squadlink.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Login : Screen("login")
    object Map : Screen("map")
    object Squad : Screen("squad")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object JoinGame : Screen("join_game")
    object GameMaster : Screen("game_master")
}
