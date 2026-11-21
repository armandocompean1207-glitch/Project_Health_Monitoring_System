package com.cse3310.projecthealthmonitoringsystem.monitoring

import android.content.Context
import com.cse3310.projecthealthmonitoringsystem.PhmsApplication
import com.cse3310.projecthealthmonitoringsystem.data.SeedData

object CurrentUser {
    // Uses the debug account until Login sets the real user.
    var userId: String = SeedData.DEBUG_USER_ID
}

fun Context.phms(): PhmsApplication = applicationContext as PhmsApplication
