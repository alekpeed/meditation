package com.meditation.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meditation.app.MeditationApp
import com.meditation.app.ui.screen.ActiveTimerScreen
import com.meditation.app.ui.screen.HistoryScreen
import com.meditation.app.ui.screen.HomeScreen
import com.meditation.app.ui.screen.SessionsScreen
import com.meditation.app.ui.screen.SettingsScreen
import com.meditation.app.ui.screen.SoundsScreen
import com.meditation.app.ui.theme.MeditationTheme

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* handled gracefully either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotifications()

        setContent {
            MeditationTheme {
                Surface {
                    val container = MeditationApp.from(this).container
                    val vm: MeditationViewModel = viewModel(factory = MeditationViewModel.Factory(container))
                    RootScaffold(vm)
                }
            }
        }
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Sessions("sessions", "Sessions", Icons.Outlined.SelfImprovement),
    Sounds("sounds", "Sounds", Icons.Outlined.GraphicEq),
    History("history", "History", Icons.Outlined.History),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

@Composable
private fun RootScaffold(vm: MeditationViewModel) {
    val navController = rememberNavController()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination
            NavigationBar {
                Dest.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.Home.route) { HomeScreen(vm) }
            composable(Dest.Sessions.route) { SessionsScreen(vm) }
            composable(Dest.Sounds.route) { SoundsScreen(vm) }
            composable(Dest.History.route) { HistoryScreen(vm) }
            composable(Dest.Settings.route) { SettingsScreen(vm) }
        }
    }

    // The Active Timer takes over the whole screen while a session is running (screen-flow §3).
    AnimatedVisibility(
        visible = snapshot != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        snapshot?.let { ActiveTimerScreen(it, vm) }
    }
}
