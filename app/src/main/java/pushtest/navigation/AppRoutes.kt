package pushtest.navigation

import androidx.compose.ui.graphics.vector.ImageVector

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