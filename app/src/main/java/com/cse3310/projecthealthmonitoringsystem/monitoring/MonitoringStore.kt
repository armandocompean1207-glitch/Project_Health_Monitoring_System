package com.cse3310.projecthealthmonitoringsystem.monitoring

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

object MonitoringStore {

    private const val PREFS = "phms_monitoring"
    private const val KEY_DESIGNEES = "designees"
    private const val KEY_DOSES = "doses"
    private const val KEY_ALERTS = "alerts"
    private const val KEY_NEXT_ID = "next_id"

    private var prefs: SharedPreferences? = null

    val designees = mutableStateListOf<Designee>()
    val doses = mutableStateListOf<DoseEvent>()
    val alerts = mutableStateListOf<AlertRecord>()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        load()
    }

    fun nextId(): Long {
        val p = requirePrefs()
        val id = p.getLong(KEY_NEXT_ID, 1L)
        p.edit().putLong(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    fun save() {
        val p = requirePrefs()
        p.edit()
            .putString(KEY_DESIGNEES, JSONArray(designees.map { it.toJson() }).toString())
            .putString(KEY_DOSES, JSONArray(doses.map { it.toJson() }).toString())
            .putString(KEY_ALERTS, JSONArray(alerts.map { it.toJson() }).toString())
            .apply()
    }

    private fun requirePrefs(): SharedPreferences =
        prefs ?: error("MonitoringStore.init(context) must be called first")

    private fun load() {
        val p = requirePrefs()
        designees.clear()
        doses.clear()
        alerts.clear()
        readArray(p.getString(KEY_DESIGNEES, null)).forEach { designees.add(it.toDesignee()) }
        readArray(p.getString(KEY_DOSES, null)).forEach { doses.add(it.toDose()) }
        readArray(p.getString(KEY_ALERTS, null)).forEach { alerts.add(it.toAlert()) }
    }

    private fun readArray(raw: String?): List<JSONObject> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun Designee.toJson() = JSONObject()
        .put("id", id).put("name", name).put("relationship", relationship)
        .put("phone", phone).put("email", email)

    private fun JSONObject.toDesignee() = Designee(
        id = getLong("id"),
        name = getString("name"),
        relationship = optString("relationship"),
        phone = optString("phone"),
        email = optString("email")
    )

    private fun DoseEvent.toJson() = JSONObject()
        .put("id", id).put("medicationName", medicationName)
        .put("scheduledAtMillis", scheduledAtMillis).put("graceMinutes", graceMinutes)
        .put("status", status.name).put("takenAtMillis", takenAtMillis ?: -1L)

    private fun JSONObject.toDose() = DoseEvent(
        id = getLong("id"),
        medicationName = getString("medicationName"),
        scheduledAtMillis = getLong("scheduledAtMillis"),
        graceMinutes = optInt("graceMinutes", 30),
        status = DoseStatus.valueOf(optString("status", DoseStatus.SCHEDULED.name)),
        takenAtMillis = optLong("takenAtMillis", -1L).takeIf { it >= 0 }
    )

    private fun AlertRecord.toJson() = JSONObject()
        .put("id", id).put("type", type.name).put("message", message)
        .put("createdAtMillis", createdAtMillis).put("sentTo", sentTo)

    private fun JSONObject.toAlert() = AlertRecord(
        id = getLong("id"),
        type = AlertType.valueOf(getString("type")),
        message = getString("message"),
        createdAtMillis = getLong("createdAtMillis"),
        sentTo = optString("sentTo")
    )
}
