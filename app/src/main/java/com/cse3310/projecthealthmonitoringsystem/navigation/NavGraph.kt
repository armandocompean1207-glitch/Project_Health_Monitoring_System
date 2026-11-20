package com.cse3310.projecthealthmonitoringsystem.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cse3310.projecthealthmonitoringsystem.monitoring.MonitoringScreen
import com.cse3310.projecthealthmonitoringsystem.search.SearchScreen

object AppRoutes {
    const val HOME = "home"
    const val MEDICATION = "medication"
    const val HEALTH = "health"
    const val NOTES = "notes"
    const val MORE = "more"

    const val DIET = "diet"
    const val MONITORING = "monitoring"
    const val SEARCH = "search"
    const val DATA_MAINTENANCE = "dataMaintenance"
    const val COMMUNICATION = "communication"
    const val ACCOUNTS = "accounts"

    val moreRoutes = setOf(
        MORE,
        DIET,
        MONITORING,
        SEARCH,
        DATA_MAINTENANCE,
        COMMUNICATION,
        ACCOUNTS
    )
}

data class TopLevelDestination(
    val label: String,
    val route: String,
    val icon: ImageVector
)

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(AppRoutes.HOME) {
            PlaceholderScreen(
                title = "Home",
                description = "Your health overview will appear here."
            )
        }

        composable(AppRoutes.MEDICATION) {
            PlaceholderScreen(
                title = "Medication",
                description = "Your medications and reminders will appear here."
            )
        }

        composable(AppRoutes.HEALTH) {
            PlaceholderScreen(
                title = "Vital Signs",
                description = "Your vital sign readings will appear here."
            )
        }

        composable(AppRoutes.NOTES) {
            PlaceholderScreen(
                title = "Notes",
                description = "Your private notes will appear here."
            )
        }

        composable(AppRoutes.MORE) {
            MoreScreen(
                onOpenSection = { route -> navController.navigate(route) }
            )
        }

        composable(AppRoutes.DIET) {
            PlaceholderScreen(
                title = "Diet and Weight",
                description = "Diet entries and weight history will appear here.",
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.MONITORING) {
            MonitoringScreen(onBack = { navController.popBackStack() })
        }

        composable(AppRoutes.SEARCH) {
            SearchScreen(onBack = { navController.popBackStack() })
        }

        composable(AppRoutes.DATA_MAINTENANCE) {
            PlaceholderScreen(
                title = "Data Maintenance",
                description = "Doctor, medication, and lookup forms will appear here.",
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.COMMUNICATION) {
            PlaceholderScreen(
                title = "Communication",
                description = "Contact and alert options will appear here.",
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.ACCOUNTS) {
            PlaceholderScreen(
                title = "Registration and Login",
                description = "Account screens will appear here.",
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun MoreScreen(
    onOpenSection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = listOf(
        "Diet and Weight" to AppRoutes.DIET,
        "Monitoring" to AppRoutes.MONITORING,
        "Search" to AppRoutes.SEARCH,
        "Data Maintenance" to AppRoutes.DATA_MAINTENANCE,
        "Communication" to AppRoutes.COMMUNICATION,
        "Registration and Login" to AppRoutes.ACCOUNTS
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("More", style = MaterialTheme.typography.headlineMedium)
        Text("Additional health tools")

        sections.forEach { (title, route) ->
            Card(
                onClick = { onOpenSection(route) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE5E7EB)
                )
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Text("← Back to More")
            }
        }

        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(description, style = MaterialTheme.typography.bodyLarge)
    }
}
