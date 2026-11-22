package pushtest.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = listOf(
        "Medication" to AppRoutes.MEDICATION,
        "Vital Signs" to AppRoutes.HEALTH,
        "Notes" to AppRoutes.NOTES,
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
        Text(
            text = "Health Monitoring System",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Welcome! Select an option below.",
            style = MaterialTheme.typography.bodyLarge
        )

        sections.forEach { (title, route) ->
            Card(
                onClick = { onNavigate(route) },
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