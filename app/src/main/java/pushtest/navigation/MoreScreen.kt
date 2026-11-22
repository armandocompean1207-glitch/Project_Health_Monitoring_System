package pushtest.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MoreScreen(
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
        Text(
            "More",
            style = MaterialTheme.typography.headlineMedium
        )

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