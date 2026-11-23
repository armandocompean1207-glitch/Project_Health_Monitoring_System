package com.cse3310.projecthealthmonitoringsystem.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.ui.components.*

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val id = CurrentUser.userId
    val prefs = rememberMeasurementPreferences()
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        onSurface = androidx.compose.ui.graphics.Color.Black,
        onSurfaceVariant = androidx.compose.ui.graphics.Color.Black,
        onBackground = androidx.compose.ui.graphics.Color.Black)) {
    PhmsPage {
        PhmsScreenHeader("Settings", "Choose how your measurements appear.", PhmsIcons.DataMaintenance, onBack)
        Text("Measurements", style = MaterialTheme.typography.titleLarge)
        ListItem(headlineContent = { Text("Temperature") }, supportingContent = { Text(if (prefs.fahrenheit) "Fahrenheit (°F)" else "Celsius (°C)") },
            trailingContent = { Switch(prefs.fahrenheit, { saveMeasurementPreferences(context, id, prefs.copy(fahrenheit = it)) }) })
        ListItem(headlineContent = { Text("Weight") }, supportingContent = { Text(if (prefs.pounds) "Pounds (lbs)" else "Kilograms (kg)") },
            trailingContent = { Switch(prefs.pounds, { saveMeasurementPreferences(context, id, prefs.copy(pounds = it)) }) })
        ListItem(headlineContent = { Text("Height") }, supportingContent = { Text(if (prefs.imperialHeight) "Feet and inches" else "Centimeters") },
            trailingContent = { Switch(prefs.imperialHeight, { saveMeasurementPreferences(context, id, prefs.copy(imperialHeight = it)) }) })
        Text("Changing units changes the display, not your saved readings.", style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text("Notifications", style = MaterialTheme.typography.titleLarge)
        ListItem(headlineContent = { Text("Medication reminders and alerts") },
            trailingContent = {
                PhmsTextButton(onClick = {
                    val intent = if (android.os.Build.VERSION.SDK_INT >= 26)
                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}"))
                    runCatching { context.startActivity(intent) }.onFailure {
                        android.widget.Toast.makeText(context, "Open PHMS notification settings in your device settings.",
                            android.widget.Toast.LENGTH_LONG).show()
                    }
                }) { Text("Manage") }
            })
    }
    }
}
