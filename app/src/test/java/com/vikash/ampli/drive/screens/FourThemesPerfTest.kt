package com.vikash.ampli.drive.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rough cost of each home theme at 1280x800: first frame, a redraw, and Home root recompositions
 * while idle (must be none: nothing on Home animates unless Ampli is listening). Numbers go to
 * build/outputs/roborazzi/perf-fourthemes-home.png. Software rendering on the JVM, so compare rows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "en-w1280dp-h800dp-land-mdpi")
class FourThemesPerfTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: DriveApp get() = RuntimeEnvironment.getApplication() as DriveApp

    private fun drawFrame() {
        val root = compose.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        bmp.recycle()
    }

    @Test
    fun homeIsQuietWhileIdle() {
        app.prefs.setNameAsked()
        app.shell.homeCardDismissed = true
        app.prefs.setProTestUnlock(true)
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
        for (id in ThemeDefaults.IDS) {
            val t0 = System.nanoTime()
            app.prefs.setTheme(ThemeChoice(id))
            compose.mainClock.advanceTimeBy(1_000L)
            drawFrame()
            val firstMs = (System.nanoTime() - t0) / 1_000_000
            val comps0 = homeCompositions
            compose.mainClock.advanceTimeBy(2_000L)
            val idleComps = homeCompositions - comps0
            repeat(3) { drawFrame() }
            val frames = 10
            val t1 = System.nanoTime()
            repeat(frames) { drawFrame() }
            val frameMs = (System.nanoTime() - t1) / 1_000_000.0 / frames
            out += "%-12s first frame %5d ms · redraw %6.1f ms · idle root recompositions %d".format(id, firstMs, frameMs, idleComps)
            assertEquals("$id: Home must not recompose while idle", 0, idleComps)
        }
        println(out.joinToString("\n"))
        report = out
        compose.mainClock.advanceTimeBy(500L)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/perf-fourthemes-home.png")
    }
}
