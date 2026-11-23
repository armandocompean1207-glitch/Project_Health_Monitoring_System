package com.cse3310.projecthealthmonitoringsystem.monitoring

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.cse3310.projecthealthmonitoringsystem.PhmsApplication
import com.cse3310.projecthealthmonitoringsystem.data.SeedData

object CurrentUser {
    var userId: String by androidx.compose.runtime.mutableStateOf("")
        private set
    internal fun signIn(id: String) { userId = id }
    internal fun clear() { userId = "" }
}

fun Context.phms(): PhmsApplication = applicationContext as PhmsApplication
