package com.cse3310.projecthealthmonitoringsystem.search

import android.content.Context
import org.json.JSONArray

/** Account-specific local search history. Legacy shared history is deliberately not imported. */
class SearchHistory(context: Context, userId: String) {
    private val prefs = context.applicationContext.getSharedPreferences("phms_search", Context.MODE_PRIVATE)
    private val key = "history_user_" + userId
    fun recent(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { array.getString(it) }.take(10)
    }.getOrDefault(emptyList())
    fun add(query: String) {
        val value = query.trim().take(200)
        if (value.isBlank()) return
        val values = (listOf(value) + recent().filterNot { it.equals(value, true) }).take(10)
        prefs.edit().putString(key, JSONArray(values).toString()).apply()
    }
    fun clear() { prefs.edit().remove(key).apply() }
}
