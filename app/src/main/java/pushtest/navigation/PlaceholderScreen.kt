package pushtest.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlaceholderScreen(
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

        Text(
            title,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            description,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}