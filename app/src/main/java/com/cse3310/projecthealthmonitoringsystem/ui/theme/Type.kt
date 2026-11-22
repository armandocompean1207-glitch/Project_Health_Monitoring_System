package com.cse3310.projecthealthmonitoringsystem.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import com.cse3310.projecthealthmonitoringsystem.R
import androidx.compose.ui.unit.sp

// Bundled official variable fonts: no provider, network, or silent download fallback.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun family(resource: Int) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map {
        Font(resource, weight = it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
    }
)
private val headings = family(R.font.plus_jakarta_sans)
private val body = family(R.font.inter)
private fun heading(weight: FontWeight, size: Int, line: Int) =
    TextStyle(fontFamily = headings, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)
private fun prose(size: Int, line: Int) =
    TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = line.sp)

val Typography = Typography(
    displayLarge = heading(FontWeight.Bold, 36, 44),
    displayMedium = heading(FontWeight.Bold, 32, 40),
    displaySmall = heading(FontWeight.Bold, 28, 36),
    headlineLarge = heading(FontWeight.ExtraBold, 28, 35),
    headlineMedium = heading(FontWeight.Bold, 24, 32),
    headlineSmall = heading(FontWeight.SemiBold, 22, 30),
    titleLarge = heading(FontWeight.Bold, 18, 26),
    titleMedium = heading(FontWeight.Bold, 16, 24),
    titleSmall = heading(FontWeight.SemiBold, 14, 20),
    bodyLarge = prose(16, 24),
    bodyMedium = prose(14, 21),
    bodySmall = prose(12, 18),
    labelLarge = heading(FontWeight.Bold, 14, 20),
    labelMedium = heading(FontWeight.SemiBold, 12, 17),
    labelSmall = heading(FontWeight.SemiBold, 11, 16)
)
