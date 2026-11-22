package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import com.cse3310.projecthealthmonitoringsystem.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BundledFontTest {
    @Test fun bundledFontsLoadAndRenderDifferentMetricsFromSystemDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sample = "PHMS Your medicines 1234567890"
        val system = Paint().apply { typeface = Typeface.DEFAULT; textSize = 28f }.measureText(sample)
        for (id in listOf(R.font.plus_jakarta_sans, R.font.inter)) {
            val font = context.resources.getFont(id)
            assertNotNull(font)
            assertNotEquals(Typeface.DEFAULT, font)
            val width = Paint().apply { typeface = font; textSize = 28f }.measureText(sample)
            assertTrue("Bundled typeface must have real glyph metrics", width > 0)
            assertTrue("Font must not silently fall back to the system face", kotlin.math.abs(width - system) > 0.1f)
        }
    }
}
