package com.cse3310.projecthealthmonitoringsystem.search

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray

object SearchHistory {

    private const val PREFS = "phms_search"
    private const val KEY = "history"
    private const val MAX = 10

    private var prefs: SharedPreferences? = null
    val recent = mutableStateListOf<String>()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val array = JSONArray(p.getString(KEY, "[]"))
        recent.clear()
        (0 until array.length()).forEach { recent.add(array.getString(it)) }
    }

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        recent.removeAll { it.equals(q, ignoreCase = true) }
        recent.add(0, q)
        while (recent.size > MAX) recent.removeAt(recent.size - 1)
        prefs?.edit()?.putString(KEY, JSONArray(recent.toList()).toString())?.apply()
    }

    fun clear() {
        recent.clear()
        prefs?.edit()?.remove(KEY)?.apply()
    }
}
