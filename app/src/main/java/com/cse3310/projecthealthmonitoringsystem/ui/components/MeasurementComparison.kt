package com.cse3310.projecthealthmonitoringsystem.ui.components

import java.math.BigDecimal
import java.math.RoundingMode

/** Display only: stored readings, units and clinical interpretation are unchanged. */
fun measurementComparison(current: Double, previous: Double, unit: String, precision: Int = 2): String? {
    if (!current.isFinite() || !previous.isFinite()) return null
    val difference = BigDecimal.valueOf(current).subtract(BigDecimal.valueOf(previous))
        .setScale(precision.coerceIn(0, 6), RoundingMode.HALF_UP)
    if (difference.signum() == 0) return "No change since last time"
    val amount = difference.abs().stripTrailingZeros().toPlainString()
    return "$amount ${unit.trim()} ${if (difference.signum() > 0) "higher" else "lower"} than last time".replace("  ", " ")
}
