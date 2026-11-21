package com.cse3310.projecthealthmonitoringsystem.medication

object Frequency {

    fun label(timesPerDay: Int): String = when (timesPerDay) {
        1 -> "Once a day"
        2 -> "Twice a day"
        else -> "$timesPerDay times a day"
    }

    fun timesPerDay(frequency: String): Int {
        val f = frequency.trim().lowercase()
        return when {
            f.startsWith("once") -> 1
            f.startsWith("twice") -> 2
            else -> f.split(" ").firstOrNull()?.toIntOrNull()?.coerceIn(1, 6) ?: 1
        }
    }
}
