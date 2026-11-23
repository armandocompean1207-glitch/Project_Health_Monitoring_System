package com.cse3310.projecthealthmonitoringsystem.maintenance

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class AppointmentOpenRequest(val owner: String, val id: Long, val token: Long = System.nanoTime())
object AppointmentNavigation {
    var request by mutableStateOf<AppointmentOpenRequest?>(null)
        private set
    fun accept(intent: Intent?) {
        val owner = intent?.getStringExtra(AppointmentReminders.OWNER) ?: return
        val id = intent.getLongExtra(AppointmentReminders.ID, -1)
        if (owner.isNotBlank() && id > 0) request = AppointmentOpenRequest(owner, id)
    }
    fun consumed(value: AppointmentOpenRequest) { if (request == value) request = null }
}
