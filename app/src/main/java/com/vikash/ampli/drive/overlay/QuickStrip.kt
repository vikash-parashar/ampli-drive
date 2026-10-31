package com.vikash.ampli.drive.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.ui.QuickPanelOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Car Hub's screen state, for the strip: true while Home (which has its own pull-down), the crash
 * countdown or the SOS screen is showing. DriveRoot keeps it up to date.
 */
object QuickStripGate {
    val hubScreenBlocks = MutableStateFlow(true)
}

/**
 * "Swipe down from the top for quick controls" over Google Maps and other apps, like a phone.
 *
 * A thin transparent TYPE_APPLICATION_OVERLAY window along the top edge (middle half of the
 * width, just under the vendor status bar) watches for a downward drag; past [QuickStripLogic.OPEN_DP]
 * it opens the quick panel in a second, full-screen overlay window with Compose content. The
 * panel slides down and closes on swipe up, a tap outside it, Back, or any action.
 *
 * Uses the same "Display over other apps" permission as the speed bubble. CarMonitorService owns
 * it, so it works while the drive monitor runs.
 */
class QuickStrip(private val app: DriveApp) {
    private val wm: WindowManager? = app.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var strip: View? = null

    private var panelRoot: View? = null
    private var panelOwner: OverlayOwner? = null
    private val panelVisible = mutableStateOf(false)
    private val _panelOpen = MutableStateFlow(false)

    /** True from the moment the panel opens until its window is gone. */
    val panelOpen: StateFlow<Boolean> = _panelOpen.asStateFlow()

    fun canShow(): Boolean = Settings.canDrawOverlays(app)

    fun showStrip() {
        if (strip != null || wm == null || !canShow()) return
        val dm = app.resources.displayMetrics
        val g = QuickStripLogic.strip(dm.widthPixels, dm.density)
        val v = View(app)
        v.contentDescription = app.getString(com.vikash.ampli.drive.R.string.quick_strip_setting)
        val p = WindowManager.LayoutParams(
            g.width,
            g.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: keys and touches outside the strip go to the app below as usual.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = g.x
            y = 0
            title = "AmpliQuickStrip"
        }
        v.setOnTouchListener(StripTouch(dm.density))
        try {
            wm.addView(v, p)
        } catch (_: Exception) {
            return
        }
        strip = v
    }

    fun hideStrip() {
        strip?.let {
            try {
                wm?.removeView(it)
            } catch (_: Exception) {
            }
        }
        strip = null
    }

    fun openPanel() {
        if (panelRoot != null || wm == null || !canShow()) return
        val owner = OverlayOwner()
        val compose = ComposeView(app).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                QuickPanelOverlay(app = app, open = panelVisible.value, onClose = ::closePanel)
            }
        }
        val root = BackFrame(app) { closePanel() }.apply { addView(compose) }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Focusable so Back reaches the panel; full screen, so a tap outside the card lands on the scrim.
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "AmpliQuickPanel"
        }
        owner.start()
        try {
            wm.addView(root, p)
        } catch (_: Exception) {
            owner.destroy()
            return
        }
        panelRoot = root
        panelOwner = owner
        _panelOpen.value = true
        root.requestFocus()
        // The first frame shows nothing; flipping to visible starts the slide-down.
        main.post { panelVisible.value = true }
    }

    /** Slides the panel up, then removes its window. */
    fun closePanel() {
        if (panelRoot == null || !panelVisible.value) {
            if (panelRoot != null && !panelVisible.value) removePanel()
            return
        }
        panelVisible.value = false
        main.postDelayed(::removePanel, CLOSE_ANIM_MS)
    }

    private fun removePanel() {
        main.removeCallbacksAndMessages(null)
        panelVisible.value = false
        panelRoot?.let {
            try {
                wm?.removeView(it)
            } catch (_: Exception) {
            }
        }
        panelOwner?.destroy()
        panelRoot = null
        panelOwner = null
        _panelOpen.value = false
    }

    /** Everything off (service stopping). */
    fun release() {
        hideStrip()
        removePanel()
    }

    private inner class StripTouch(density: Float) : View.OnTouchListener {
        private val tracker = QuickStripLogic.Tracker(density)

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> tracker.down(e.rawX, e.rawY)
                MotionEvent.ACTION_MOVE -> if (tracker.move(e.rawX, e.rawY) == QuickStripLogic.Swipe.Open) openPanel()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracker.up()
            }
            return true
        }
    }

    /** Root of the panel window: Back closes the panel. */
    private class BackFrame(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        init {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    /** Lifecycle, saved state and view models for Compose in a window that has no activity. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        fun start() {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            if (registry.currentState == Lifecycle.State.INITIALIZED) {
                registry.currentState = Lifecycle.State.CREATED
            }
            registry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }

    companion object {
        private const val CLOSE_ANIM_MS = 260L
    }
}
