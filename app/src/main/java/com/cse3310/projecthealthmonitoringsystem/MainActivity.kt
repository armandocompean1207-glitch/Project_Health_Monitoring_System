package com.cse3310.projecthealthmonitoringsystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cse3310.projecthealthmonitoringsystem.navigation.AppNavGraph
import com.cse3310.projecthealthmonitoringsystem.navigation.AppRoutes
import com.cse3310.projecthealthmonitoringsystem.navigation.TopLevelDestination
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsIcons
import com.cse3310.projecthealthmonitoringsystem.ui.theme.ProjectHealthMonitoringSystemTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ProjectHealthMonitoringSystemTheme {
                com.cse3310.projecthealthmonitoringsystem.auth.AccountGate()
            }
        }
    }
}

@Composable
fun PhmsApp() {
    if (com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser.userId.isBlank()) return
    val navController = rememberNavController()
    val navigationGuard = androidx.compose.runtime.remember { com.cse3310.projecthealthmonitoringsystem.ui.components.NavigationGuard() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val destinations = listOf(
        TopLevelDestination("Home", AppRoutes.HOME, PhmsIcons.Home),
        TopLevelDestination("Meds", AppRoutes.MEDICATION, PhmsIcons.Medication),
        TopLevelDestination("Vitals", AppRoutes.HEALTH, PhmsIcons.Vitals),
        TopLevelDestination("More", AppRoutes.MORE, PhmsIcons.More)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                destinations.forEach { destination ->
                    val isMoreRoute = currentRoute in AppRoutes.moreRoutes
                    val selected = if (destination.route == AppRoutes.MORE) {
                        isMoreRoute
                    } else {
                        currentRoute == destination.route
                    }

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navigationGuard.request {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.icon else when (destination.route) {
                                    AppRoutes.HOME -> Icons.Outlined.Home
                                    AppRoutes.MEDICATION -> Icons.Outlined.Medication
                                    AppRoutes.HEALTH -> Icons.Outlined.MonitorHeart
                                    else -> Icons.Outlined.MoreHoriz
                                },
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text(destination.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    )
    { innerPadding ->
        androidx.compose.runtime.CompositionLocalProvider(
            com.cse3310.projecthealthmonitoringsystem.ui.components.LocalNavigationGuard provides navigationGuard) {
            AppNavGraph(navController = navController, modifier = Modifier.padding(innerPadding))
        }
    }
}