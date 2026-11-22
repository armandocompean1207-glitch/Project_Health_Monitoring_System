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
    PhmsPage {
        PhmsScreenHeader("Settings", "Choose how your measurements appear.", PhmsIcons.DataMaintenance, onBack)
        Text("Measurements", style = MaterialTheme.typography.titleLarge)
        ListItem(headlineContent = { Text("Temperature") }, supportingContent = { Text(if (prefs.fahrenheit) "Fahrenheit (°F)" else "Celsius (°C)") },
            trailingContent = { Switch(prefs.fahrenheit, { saveMeasurementPreferences(context, id, prefs.copy(fahrenheit = it)) }) })
        ListItem(headlineContent = { Text("Weight") }, supportingContent = { Text(if (prefs.pounds) "Pounds (lb)" else "Kilograms (kg)") },
            trailingContent = { Switch(prefs.pounds, { saveMeasurementPreferences(context, id, prefs.copy(pounds = it)) }) })
        ListItem(headlineContent = { Text("Height") }, supportingContent = { Text(if (prefs.imperialHeight) "Feet and inches" else "Centimeters") },
            trailingContent = { Switch(prefs.imperialHeight, { saveMeasurementPreferences(context, id, prefs.copy(imperialHeight = it)) }) })
        Text("Changing units changes the display, not your saved readings.", style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text("Blood pressure · mmHg\nGlucose and cholesterol · mg/dL\nDates · Month day, year\nTime · AM/PM", style = MaterialTheme.typography.bodyLarge)
    }
}
