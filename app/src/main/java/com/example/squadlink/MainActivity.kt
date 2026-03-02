package com.example.squadlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.squadlink.navigation.NavGraph
import com.example.squadlink.navigation.Screen
import com.example.squadlink.ui.theme.SquadLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SquadLinkTheme {
                SquadLinkApp()
            }
        }
    }
}


private val bottomNavScreens = listOf(
    Screen.Home.route,
    Screen.Map.route,
    Screen.Squad.route,
    Screen.Profile.route
)

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Lobby", Icons.Default.Home, Screen.Home.route),
    BottomNavItem("Mapa", Icons.Default.LocationOn, Screen.Map.route),
    BottomNavItem("Escuadrón", Icons.Default.AccountBox, Screen.Squad.route),
    BottomNavItem("Perfil", Icons.Default.Person, Screen.Profile.route),
)

@Composable
fun SquadLinkApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavScreens

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                SquadLinkBottomNav(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            startDestination = Screen.Home.route
        )
    }
}

@Composable
fun SquadLinkBottomNav(navController: NavController, currentRoute: String?) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        // Avoid building up a large back stack
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}