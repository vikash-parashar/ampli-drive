package com.vikash.ampli.drive.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.MainActivity
import com.vikash.ampli.drive.prefs.AutoStartMode
import com.vikash.ampli.drive.system.AppResolver
import com.vikash.ampli.drive.system.AppRole
import com.vikash.ampli.drive.system.Launchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What started the car-start actions. */
enum class StartTrigger { Boot, Ignition, PhoneConnected }

/** When to run the car-start actions. Plain Kotlin, unit-tested. */
object StartPolicy {
    /** One run per start: boot, ignition and a phone connecting minutes apart are the same trip. */
    const val MIN_GAP_MS = 3 * 60_000L

    fun shouldRun(lastRunAtMs: Long, nowMs: Long): Boolean = lastRunAtMs <= 0L || nowMs - lastRunAtMs !in 0 until MIN_GAP_MS

    /** Only the greeting runs on boot/ignition; a phone connecting only triggers the auto start. */
    fun greets(trigger: StartTrigger): Boolean = trigger != StartTrigger.PhoneConnected

    /** Boot is detected by a changed Settings.Global.BOOT_COUNT, or by uptime on the first run. */
    fun isNewBoot(lastBootCount: Int, bootCount: Int, uptimeMs: Long): Boolean = when {
        bootCount >= 0 && lastBootCount >= 0 -> bootCount != lastBootCount
        else -> uptimeMs < FRESH_BOOT_UPTIME_MS
    }

    const val FRESH_BOOT_UPTIME_MS = 5 * 60_000L
}

/**
 * Boot, ignition (ACC on) and "my phone connected" all end up here. The actions run from
 * MainActivity, which is in front then (it is the HOME app): Android 10+ only lets a visible app
 * start other apps. A signal that arrives while Car Hub is hidden is kept as pending and runs the
 * next time the launcher shows.
 *
 * Actions: the welcome message (WelcomeGreeter) and Settings › When the car
 * starts (open the phone-mirroring app, or start music and open the map).
 */
object CarStart {
    private val _events = MutableSharedFlow<StartTrigger>(extraBufferCapacity = 4)
    val events: SharedFlow<StartTrigger> = _events.asSharedFlow()

    /** Set while MainActivity is resumed. */
    @Volatile var launcherVisible = false

    fun signal(app: DriveApp, trigger: StartTrigger) {
        app.prefs.carStartPending = true
        pendingTrigger = trigger
        if (launcherVisible) {
            _events.tryEmit(trigger)
        } else if (trigger == StartTrigger.Ignition) {
            // Ignition on while another app is in front: ask for the launcher. Android may refuse a
            // background start (10+); the pending flag covers that.
            try {
                app.startActivity(
                    Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            } catch (_: Exception) {
            }
        }
    }

    @Volatile private var pendingTrigger: StartTrigger = StartTrigger.Boot

    /** MainActivity.onResume: a reboot noticed by boot count, or a pending signal. */
    fun takePending(context: Context, app: DriveApp): StartTrigger? {
        val prefs = app.prefs
        val bootCount = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: Exception) {
            -1
        }
        val newBoot = StartPolicy.isNewBoot(prefs.lastBootCount, bootCount, SystemClock.elapsedRealtime())
        if (bootCount >= 0) prefs.lastBootCount = bootCount
        val pending = prefs.carStartPending
        if (!newBoot && !pending) return null
        prefs.carStartPending = false
        return if (newBoot) StartTrigger.Boot else pendingTrigger
    }

    /** Runs the greeting and the auto start. Call from the visible launcher (lifecycleScope). */
    suspend fun run(context: Context, app: DriveApp, trigger: StartTrigger) {
        val prefs = app.prefs
        val now = System.currentTimeMillis()
        if (!StartPolicy.shouldRun(prefs.lastCarStartAt, now)) return
        val settings = prefs.autoStart.value
        if (trigger == StartTrigger.PhoneConnected && settings.mode == AutoStartMode.Off) return
        prefs.lastCarStartAt = now

        // Welcome audio: "Ram Ram {name} Ji, aaj kit ka plan hai?" (Haryanvi) or a plain welcome (Standard).
        if (StartPolicy.greets(trigger)) app.welcome.play()

        if (settings.mode == AutoStartMode.Off) return
        delay(settings.delaySec.coerceIn(0, 120) * 1000L)
        when (settings.mode) {
            AutoStartMode.Mirror -> {
                val pkg = settings.mirrorPackage ?: AppResolver.bestGuess(context, AppRole.Mirror)?.packageName
                if (pkg != null) Launchers.openPackage(context, pkg, quiet = true)
            }
            AutoStartMode.MapsMusic -> {
                startMusic(context, app)
                delay(MUSIC_SETTLE_MS)
                val nav = settings.navPackage
                    ?: Launchers.GOOGLE_MAPS.takeIf { context.packageManager.getLaunchIntentForPackage(it) != null }
                    ?: AppResolver.bestGuess(context, AppRole.Nav)?.packageName
                // Google Maps resumes the route it was navigating by itself when opened.
                if (nav != null) Launchers.openPackage(context, nav, quiet = true)
                if (settings.returnAfterSec > 0) {
                    delay(settings.returnAfterSec * 1000L)
                    try {
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                        )
                    } catch (_: Exception) {
                    }
                }
            }
            AutoStartMode.Off -> Unit
        }
    }

    /**
     * Music in the chosen app: open it, then press "play" through its media session, a media
     * button sent to it, or the system's last media app, whichever works.
     */
    private suspend fun startMusic(context: Context, app: DriveApp) {
        val pkg = app.prefs.musicPackage.value
            ?: app.prefs.lastMediaPackage
            ?: AppResolver.bestGuess(context, AppRole.Music)?.packageName
        if (pkg != null) Launchers.openPackage(context, pkg, quiet = true)
        delay(MUSIC_SETTLE_MS)
        if (pkg != null && app.mediaHub.play(pkg)) return
        if (pkg != null) {
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                try {
                    context.sendBroadcast(
                        Intent(Intent.ACTION_MEDIA_BUTTON)
                            .setPackage(pkg)
                            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)),
                    )
                } catch (_: Exception) {
                }
            }
        }
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (!audio.isMusicActive) {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        }
    }

    private const val MUSIC_SETTLE_MS = 2_500L
}

/**
 * BOOT_COMPLETED (and the quick-boot twins head units send instead of a real boot). It only marks
 * the start as pending: the launcher is the HOME app, so it shows right after boot and runs the
 * actions from the foreground (see [CarStart]).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? DriveApp ?: return
        val trigger = if (intent.action == Intent.ACTION_BOOT_COMPLETED) StartTrigger.Boot else StartTrigger.Ignition
        CarStart.signal(app, trigger)
    }
}
