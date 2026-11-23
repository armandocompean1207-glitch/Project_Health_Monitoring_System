package com.cse3310.projecthealthmonitoringsystem.monitoring

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DoseTimes {

    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun formatTime(millis: Long): String = SimpleDateFormat("MM/dd/yyyy · h:mm a", Locale.US).format(Date(millis))

    fun parseTime(text: String): Pair<Int, Int>? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    fun minutesOf(text: String): Int? = parseTime(text)?.let { (h, m) -> h * 60 + m }

    fun timeText(minutes: Int): String = "%02d:%02d".format(Locale.US, minutes / 60, minutes % 60)

    fun displayClock(minutes: Int): String = "%d:%02d %s".format(Locale.US,
        (minutes / 60 % 12).let { if (it == 0) 12 else it }, minutes % 60,
        if (minutes < 720) "AM" else "PM")

    fun nextDoseTime(hour: Int, minute: Int, timesPerDay: Int, now: Long): Long {
        val interval = DAY_MILLIS / timesPerDay.coerceIn(1, 6)
        val firstToday = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        var t = firstToday - DAY_MILLIS
        while (t <= now) t += interval
        return t
    }

    fun nextAfter(previous: Long, repeatEvery: Long, now: Long): Long {
        var t = previous + repeatEvery
        while (t <= now) t += repeatEvery
        return t
    }

    fun isPastWindow(scheduledAt: Long, graceMinutes: Int, now: Long): Boolean =
        now >= scheduledAt + graceMinutes * 60_000L

    fun todayRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        return start to end - 1
    }
}
