package pushtest.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val medicationViewModel: MedicationViewModel = viewModel()
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
            HomeScreen(
                onNavigate = { route ->
                    navController.navigate(route)
                }
            )
        }

        composable(AppRoutes.MEDICATION) {
            MedicationScreen(
                viewModel = medicationViewModel,
                onExit = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(AppRoutes.HEALTH) {
            VitalSignsScreen(
                onExit = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(AppRoutes.NOTES) {
            NotesScreen(
                onExit = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(AppRoutes.MORE) {
            MoreScreen(
                onOpenSection = { route ->
                    navController.navigate(route)
                }
            )
        }

        composable(AppRoutes.DIET) {
            DietWeightScreen(
                onExit = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(AppRoutes.MONITORING) {
            MonitoringScreen(
                medications = medicationViewModel.medications,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.SEARCH) {
            PlaceholderScreen(
                title = "Search",
                description = "Search results will appear here.",
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(AppRoutes.DATA_MAINTENANCE) {
            PlaceholderScreen(
                title = "Data Maintenance",
                description = "Doctor, medication, and lookup forms will appear here.",
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(AppRoutes.COMMUNICATION) {
            PlaceholderScreen(
                title = "Communication",
                description = "Contact and alert options will appear here.",
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(AppRoutes.ACCOUNTS) {
            RegistrationScreen(
                onExit = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}