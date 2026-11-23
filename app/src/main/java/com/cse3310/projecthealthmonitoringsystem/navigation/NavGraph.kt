package com.cse3310.projecthealthmonitoringsystem.navigation

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.cse3310.projecthealthmonitoringsystem.communication.CommunicationScreen
import com.cse3310.projecthealthmonitoringsystem.home.HomeDashboardRoute
import com.cse3310.projecthealthmonitoringsystem.maintenance.DataMaintenanceScreen
import com.cse3310.projecthealthmonitoringsystem.medication.MedicationScreen
import com.cse3310.projecthealthmonitoringsystem.monitoring.MonitoringScreen
import com.cse3310.projecthealthmonitoringsystem.search.SearchScreen
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsActionCard
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsEmptyStateCard
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsIcons
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsPage
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsSectionHeader

object AppRoutes {
    const val CHECKUPS = "checkups"
    const val SETTINGS = "settings"
    const val HOME = "home"
    const val MEDICATION = "medication"
    const val HEALTH = "health"
    const val PROFILE = "profile"
    const val NOTES = "notes"
    const val MORE = "more"

    const val DIET = "diet"
    const val WEIGHT = "diet/weight"
    const val MONITORING = "monitoring"
    const val SEARCH = "search"
    const val DATA_MAINTENANCE = "dataMaintenance"
    const val COMMUNICATION = "communication"
    const val COMMUNICATION_PATTERN = "communication?alertId={alertId}"
    const val ACCOUNTS = "accounts"
    const val APPOINTMENTS = "appointments"
    const val APPOINTMENT_DETAIL = "appointment/{visitId}"
    const val HEALTH_GUIDE = "healthGuide"

    val moreRoutes = setOf(
        MORE,
        SETTINGS,
        CHECKUPS,
        PROFILE,
        NOTES,
        DIET,
        WEIGHT,
        MONITORING,
        SEARCH,
        DATA_MAINTENANCE,
        COMMUNICATION,
        COMMUNICATION_PATTERN,
        ACCOUNTS,
        APPOINTMENTS,
        APPOINTMENT_DETAIL,
        HEALTH_GUIDE
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var lastVisitedRoute by rememberSaveable { mutableStateOf(AppRoutes.HEALTH) }

    LaunchedEffect(currentRoute) {
        if (
            currentRoute != null &&
                currentRoute !in setOf(
                    AppRoutes.HOME,
                    AppRoutes.MORE,
                    AppRoutes.PROFILE,
                    AppRoutes.ACCOUNTS
                )
        ) {
            lastVisitedRoute = currentRoute
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(durationMillis = 160)) },
        exitTransition = { fadeOut(animationSpec = tween(durationMillis = 120)) },
        popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 160)) },
        popExitTransition = { fadeOut(animationSpec = tween(durationMillis = 120)) }
    ) {
        composable(AppRoutes.HOME) {
            HomeDashboardRoute(
                onUpdateProfileClick = {
                    navController.navigate(AppRoutes.PROFILE)
                },
                onMedicationClick = {
                    navController.navigate(AppRoutes.MEDICATION)
                },
                onVitalsClick = {
                    navController.navigate(AppRoutes.HEALTH)
                },
                onAppointmentsClick = {
                    navController.navigate(AppRoutes.APPOINTMENTS)
                },
                onAlertsClick = {
                    navController.navigate(AppRoutes.MONITORING)
                },
                onDietClick = { navController.navigate(AppRoutes.DIET) },
                onWeightClick = { navController.navigate(AppRoutes.WEIGHT) },
                onNotesClick = { navController.navigate(AppRoutes.NOTES) },
                onMoreClick = { navController.navigate(AppRoutes.SETTINGS) },
                onSearchClick = { navController.navigate(AppRoutes.SEARCH) },
                onMedicalFormsClick = { navController.navigate(AppRoutes.DATA_MAINTENANCE) },
                onNotifyAlert = { id -> navController.navigate("${AppRoutes.COMMUNICATION}?alertId=$id") }
            )
        }

        composable(AppRoutes.MEDICATION) {
            MedicationScreen()
        }

        composable(AppRoutes.HEALTH) {
            com.cse3310.projecthealthmonitoringsystem.vitals.VitalSignsRoute(
                onNotifyContact = { id -> navController.navigate("${AppRoutes.COMMUNICATION}?alertId=$id") }
            )
        }

        composable(AppRoutes.SETTINGS) {
            com.cse3310.projecthealthmonitoringsystem.settings.SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.PROFILE) {
            com.cse3310.projecthealthmonitoringsystem.auth.ProfileScreen(
                onBack = { navController.popBackStack() },
                onDoctors = { navController.navigate(AppRoutes.DATA_MAINTENANCE) },
                onAppointments = { navController.navigate(AppRoutes.APPOINTMENTS) }
            )
        }

        composable(AppRoutes.CHECKUPS) {
            DataMaintenanceScreen(onBack = { navController.popBackStack() }, initialCheckups = true,
                onOpenMedication = { navController.navigate(AppRoutes.MEDICATION) })
        }
        composable(AppRoutes.HEALTH_GUIDE) {
            com.cse3310.projecthealthmonitoringsystem.healthguide.HealthGuideScreen(
                onBack = { navController.popBackStack() },
                onVitals = { navController.navigate(AppRoutes.HEALTH) },
                onMedicines = { navController.navigate(AppRoutes.MEDICATION) },
                onAppointments = { navController.navigate(AppRoutes.APPOINTMENTS) },
                onNotes = { navController.navigate(AppRoutes.NOTES) },
                onCheckups = { navController.navigate(AppRoutes.CHECKUPS) }
            )
        }

        composable(AppRoutes.NOTES) {
            com.cse3310.projecthealthmonitoringsystem.notes.NotesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.MORE) {
            MoreScreen(
                onOpenSection = { route -> navController.navigate(route) }
            )
        }

        composable(AppRoutes.APPOINTMENT_DETAIL,
            arguments = listOf(navArgument("visitId") { type = NavType.LongType })
        ) { entry ->
            DataMaintenanceScreen(onBack = { navController.popBackStack() }, initialAppointments = true,
                initialVisitId = entry.arguments?.getLong("visitId"))
        }
        composable(AppRoutes.APPOINTMENTS) {
            DataMaintenanceScreen(
                onBack = { navController.popBackStack() },
                onOpenMedication = { navController.navigate(AppRoutes.MEDICATION) },
                initialAppointments = true
            )
        }

        composable(AppRoutes.DIET) {
            com.cse3310.projecthealthmonitoringsystem.diet.DietWeightScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.WEIGHT) {
            com.cse3310.projecthealthmonitoringsystem.diet.DietWeightScreen(
                onBack = { navController.popBackStack() }, initialTab = 1
            )
        }

        composable(AppRoutes.MONITORING) {
            MonitoringScreen(
                onBack = { navController.popBackStack() },
                onOpenCommunication = { navController.navigate(AppRoutes.COMMUNICATION) },
                onAlertCommunication = { id -> navController.navigate("${AppRoutes.COMMUNICATION}?alertId=$id") }
            )
        }

        composable(AppRoutes.SEARCH) {
            SearchScreen(onBack = { navController.popBackStack() }, onOpenModule = { kind ->
                navController.navigate(when (kind) {
                    "MEDICATION", "DOSE_LOG" -> AppRoutes.MEDICATION
                    "DOCTOR_VISIT" -> AppRoutes.APPOINTMENTS
                    "DOCTOR" -> AppRoutes.DATA_MAINTENANCE
                    "VITAL_SIGN" -> AppRoutes.HEALTH
                    "DIET_ENTRY", "WEIGHT" -> AppRoutes.DIET
                    "CHECKUP" -> AppRoutes.PROFILE
                    else -> AppRoutes.NOTES
                })
            })
        }

        composable(AppRoutes.DATA_MAINTENANCE) {
            DataMaintenanceScreen(onBack = { navController.popBackStack() },
                onOpenMedication = { navController.navigate(AppRoutes.MEDICATION) })
        }

        composable(AppRoutes.COMMUNICATION_PATTERN,
            arguments = listOf(navArgument("alertId") { type = NavType.LongType; defaultValue = -1L })
        ) { entry ->
            CommunicationScreen(onBack = { navController.popBackStack() },
                alertId = entry.arguments?.getLong("alertId")?.takeIf { it > 0 })
        }

        composable(AppRoutes.ACCOUNTS) {
            com.cse3310.projecthealthmonitoringsystem.auth.ProfileScreen(
                onBack = { navController.popBackStack() },
                onDoctors = { navController.navigate(AppRoutes.DATA_MAINTENANCE) },
                onAppointments = { navController.navigate(AppRoutes.APPOINTMENTS) }
            )
        }
    }
}

private data class MoreMenuItem(
    val title: String,
    val description: String,
    val route: String,
    val icon: ImageVector
)

@Composable
private fun MoreScreen(
    onOpenSection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = listOf(
        "Your care" to listOf(
            MoreMenuItem("Doctors & appointments", "Keep your next visit and care team in view.",
                AppRoutes.APPOINTMENTS, PhmsIcons.Appointments),
            MoreMenuItem(
                "Health Guide",
                "Keep routine checkups and reminders in view.",
                AppRoutes.HEALTH_GUIDE,
                PhmsIcons.WellGuide
            ),
            MoreMenuItem(
                "Notes",
                "Save a thought or question for later.",
                AppRoutes.NOTES,
                PhmsIcons.Notes
            ),
            MoreMenuItem(
                "Diet and Weight",
                "Keep track of meals, nutrition, and weight.",
                AppRoutes.DIET,
                PhmsIcons.Diet
            ),
            MoreMenuItem(
                "Monitoring",
                "See alerts about readings or medicines.",
                AppRoutes.MONITORING,
                PhmsIcons.Alerts
            )
        ),
        "Your information" to listOf(
            MoreMenuItem("Settings", "Units and measurement preferences.", AppRoutes.SETTINGS, PhmsIcons.DataMaintenance),
            MoreMenuItem(
                "Search",
                "Look up notes, readings, and other saved details.",
                AppRoutes.SEARCH,
                PhmsIcons.Search
            ),
            MoreMenuItem(
                "Providers & pharmacies",
                "Manage your providers, pharmacies, visits and health options.",
                AppRoutes.DATA_MAINTENANCE,
                PhmsIcons.Providers
            ),
            MoreMenuItem(
                "Communication",
                "Choose who can be contacted about care alerts.",
                AppRoutes.COMMUNICATION,
                PhmsIcons.Communication
            ),
            MoreMenuItem(
                "Personal profile",
                "Update personal details or manage your account.",
                AppRoutes.ACCOUNTS,
                PhmsIcons.Account
            )
        )
    )

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDF48),
        onPrimaryContainer = androidx.compose.ui.graphics.Color.Black,
        onSurfaceVariant = androidx.compose.ui.graphics.Color.Black)) {
    PhmsPage(modifier = modifier) {
        com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsScreenHeader(
            "More", "Your care, records and personal details.", PhmsIcons.More)

        groups.forEach { (groupTitle, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PhmsSectionHeader(title = groupTitle)
                com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard {
                    items.forEachIndexed { index, item ->
                        androidx.compose.material3.ListItem(
                            modifier = Modifier.then(Modifier.clickable { onOpenSection(item.route) }),
                            headlineContent = { Text(item.title, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text(item.description, style = MaterialTheme.typography.bodyMedium) },
                            leadingContent = { com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsIconBadge(item.icon) },
                            trailingContent = { androidx.compose.material3.Icon(PhmsIcons.Next, null) })
                        if (index < items.lastIndex) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "← Back to More"
) {
    PhmsPage(modifier = modifier) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Text(backLabel)
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        PhmsEmptyStateCard(
            title = "This section is not connected yet",
            description = description,
            icon = iconForSection(title)
        )
    }
}

private fun iconForSection(title: String): ImageVector = when (title) {
    "Medication" -> PhmsIcons.Medication
    "Vital Signs" -> PhmsIcons.Vitals
    "Profile" -> PhmsIcons.Profile
    "Checkup checklist" -> PhmsIcons.WellGuide
    "Notes" -> PhmsIcons.Notes
    "Appointments" -> PhmsIcons.Appointments
    "Diet and Weight" -> PhmsIcons.Diet
    "Monitoring" -> PhmsIcons.Alerts
    "Search" -> PhmsIcons.Search
    "Data Maintenance" -> PhmsIcons.DataMaintenance
    "Communication" -> PhmsIcons.Communication
    "Registration and Login" -> PhmsIcons.Account
    else -> PhmsIcons.Brand
}

private fun routeTitle(route: String): String = when (route) {
    AppRoutes.MEDICATION -> "Medication"
    AppRoutes.HEALTH -> "Vital Signs"
    AppRoutes.NOTES -> "Notes"
    AppRoutes.DIET -> "Diet and Weight"
    AppRoutes.MONITORING -> "Monitoring"
    AppRoutes.SEARCH -> "Search"
    AppRoutes.DATA_MAINTENANCE -> "Data Maintenance"
    AppRoutes.COMMUNICATION -> "Communication"
    AppRoutes.APPOINTMENTS -> "Appointments"
    AppRoutes.HEALTH_GUIDE -> "Health Guide"
    else -> "Vital Signs"
}
