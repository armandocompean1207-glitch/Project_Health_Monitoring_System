package com.cse3310.projecthealthmonitoringsystem.settings

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.data.entity.WeightUnit

data class MeasurementPreferences(val fahrenheit: Boolean = true, val pounds: Boolean = true, val imperialHeight: Boolean = true) {
    val weightUnit get() = if (pounds) WeightUnit.LB else WeightUnit.KG
    val weightLabel get() = if (pounds) "lbs" else "kg"
}
object MeasurementUnits {
    fun fahrenheit(c: Double) = c * 9 / 5 + 32
    fun celsius(f: Double) = (f - 32) * 5 / 9
    fun inches(cm: Double) = cm / 2.54
    fun centimeters(feet: Double, inches: Double) = (feet * 12 + inches) * 2.54
    fun temperature(name: String) = name.contains("temperature", true)
    fun displayVital(value: Double, name: String, storedUnit: String, prefs: MeasurementPreferences): Double =
        if (!temperature(name)) value else if (prefs.fahrenheit && !storedUnit.contains("F", true)) fahrenheit(value)
        else if (!prefs.fahrenheit && storedUnit.contains("F", true)) celsius(value) else value
    fun storedVital(value: Double, name: String, storedUnit: String, prefs: MeasurementPreferences): Double =
        if (!temperature(name)) value else if (prefs.fahrenheit && !storedUnit.contains("F", true)) celsius(value)
        else if (!prefs.fahrenheit && storedUnit.contains("F", true)) fahrenheit(value) else value
    fun displayUnit(name: String, unit: String, prefs: MeasurementPreferences) =
        if (temperature(name)) if (prefs.fahrenheit) "°F" else "°C" else unit
}
private fun key(id: String, name: String) = "$id:$name"
fun loadMeasurementPreferences(context: Context, id: String): MeasurementPreferences {
    val p = context.getSharedPreferences("measurement_preferences", Context.MODE_PRIVATE)
    return MeasurementPreferences(p.getBoolean(key(id, "fahrenheit"), true), p.getBoolean(key(id, "pounds"), true),
        p.getBoolean(key(id, "imperial_height"), true))
}
fun saveMeasurementPreferences(context: Context, id: String, value: MeasurementPreferences) {
    context.getSharedPreferences("measurement_preferences", Context.MODE_PRIVATE).edit()
        .putBoolean(key(id, "fahrenheit"), value.fahrenheit).putBoolean(key(id, "pounds"), value.pounds)
        .putBoolean(key(id, "imperial_height"), value.imperialHeight).apply()
}
@Composable
fun rememberMeasurementPreferences(): MeasurementPreferences {
    val context = LocalContext.current
    val id = CurrentUser.userId
    val p = remember(context) { context.getSharedPreferences("measurement_preferences", Context.MODE_PRIVATE) }
    var value by remember(id) { mutableStateOf(loadMeasurementPreferences(context, id)) }
    DisposableEffect(p, id) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value = loadMeasurementPreferences(context, id) }
        p.registerOnSharedPreferenceChangeListener(listener)
        onDispose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}
