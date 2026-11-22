package com.cse3310.projecthealthmonitoringsystem.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*

class NavigationGuard {
    var interceptor: (((() -> Unit)) -> Unit)? = null
    fun request(action: () -> Unit) {
        val check = interceptor
        if (check == null) action() else check(action)
    }
}
val LocalNavigationGuard = staticCompositionLocalOf<NavigationGuard?> { null }

/** One confirmation for toolbar, system back, cancel, and primary-tab navigation. */
@Composable
fun rememberGuardedDismiss(dirty: Boolean, busy: Boolean = false, onDismiss: () -> Unit): () -> Unit {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val guard = LocalNavigationGuard.current
    val dismiss = { if (!busy) { if (dirty) pending = onDismiss else onDismiss() }; Unit }
    BackHandler(enabled = dirty || busy) { dismiss() }
    DisposableEffect(guard, dirty, busy) {
        val intercept: (() -> Unit) -> Unit = { action -> if (!busy) { if (dirty) pending = action else action() } }
        if (dirty || busy) guard?.interceptor = intercept
        onDispose { if (guard?.interceptor === intercept) guard.interceptor = null }
    }
    if (pending != null) AlertDialog(onDismissRequest = { pending = null },
        title = { Text("Discard changes?") }, text = { Text("Your unsaved changes will be lost.") },
        confirmButton = { PhmsTextButton(onClick = { val action = pending; pending = null; action?.invoke() }) { Text("Discard") } },
        dismissButton = { PhmsTextButton(onClick = { pending = null }) { Text("Keep editing") } })
    return dismiss
}
