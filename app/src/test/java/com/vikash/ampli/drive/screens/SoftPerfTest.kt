package com.vikash.ampli.drive.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.prefs.ThemeChoice
import com.vikash.ampli.drive.prefs.ThemeDefaults
import com.vikash.ampli.drive.ui.DriveRoot
import com.vikash.ampli.drive.ui.Screen
import com.vikash.ampli.drive.ui.SoftShadowCache
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rough cost of the soft look on Home, next to the old default themes, at 1280x800: Home
 * recompositions while idle, the time to draw a frame (software rendering on the JVM, so only the
 * comparison means something), and how many shadow templates the soft themes had to render.
 * The numbers are drawn into build/outputs/roborazzi/perf-soft-home.png; the test fails only if the
 * shadow cache keeps re-rendering (it must reuse templates between frames).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "en-w1280dp-h800dp-land-mdpi")
class SoftPerfTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: DriveApp get() = RuntimeEnvironment.getApplication() as DriveApp

    @Test
    fun softHomeCostsAboutTheSame() {
        app.prefs.setNameAsked()
        app.shell.homeCardDismissed = true
        var report by mutableStateOf<List<String>?>(null)
        var homeCompositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val lines = report
            if (lines == null) {
                SideEffect { homeCompositions++ }
                DriveRoot(app = app, screen = Screen.Home, resumeTick = 0, onNavigate = {}, onRequestPermissions = {})
            } else {
                Column(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                    for (l in lines) Text(l, color = Color.Black, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        val out = mutableListOf("Home at 1280x800 (Robolectric software rendering; compare rows, not absolute ms)", "")
        val themes = listOf(
            "Ampli Rose (old night)" to ThemeDefaults.ROSE,
            "Daylight Clean (old day)" to ThemeDefaults.DAYLIGHT,
            "Ampli Soft Day" to ThemeDefaults.SOFT_DAY,
            "Ampli Soft Night" to ThemeDefaults.SOFT_NIGHT,
        )
        for ((name, id) in themes) {
            val rendersBefore = SoftShadowCache.renders
            val nanosBefore = SoftShadowCache.renderNanos
            val t0 = System.nanoTime()
            app.prefs.setTheme(ThemeChoice(auto = false, dayId = id, nightId = id, fixedId = id))
            compose.mainClock.advanceTimeBy(1_000L)
            compose.onRoot().captureToImage()
            val firstMs = (System.nanoTime() - t0) / 1_000_000
            val rendersFirst = SoftShadowCache.renders - rendersBefore
            // Idle: no recomposition of the Home root while nothing changes.
            val comps0 = homeCompositions
            compose.mainClock.advanceTimeBy(2_000L)
            val idleComps = homeCompositions - comps0
            // Redraws: the same frame again, several times.
            repeat(3) { compose.onRoot().captureToImage() }
            val frames = 10
            val t1 = System.nanoTime()
            repeat(frames) { compose.onRoot().captureToImage() }
            val frameMs = (System.nanoTime() - t1) / 1_000_000.0 / frames
            val rendersAfter = SoftShadowCache.renders - rendersBefore - rendersFirst
            out += "%-26s first frame %5d ms · redraw %6.1f ms · idle root recompositions %d".format(name, firstMs, frameMs, idleComps)
            out += "%-26s shadow templates %d (%d ms) · re-rendered while redrawing: %d".format(
                "", rendersFirst, (SoftShadowCache.renderNanos - nanosBefore) / 1_000_000, rendersAfter,
            )
            assertTrue("$name: shadow templates must be reused between frames", rendersAfter == 0)
        }
        out += ""
        out += "Shadow cache now: ${SoftShadowCache.bytes / 1024} KB"
        println(out.joinToString("\n"))
        report = out
        compose.mainClock.advanceTimeBy(500L)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/perf-soft-home.png")
    }
}
