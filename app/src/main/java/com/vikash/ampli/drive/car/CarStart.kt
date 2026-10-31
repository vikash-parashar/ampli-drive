package com.vikash.ampli.drive.car

import com.vikash.ampli.drive.ui.DriveLock
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
import com.vikash.ampli.drive.system.AmpliMusic
import com.vikash.ampli.drive.system.AppResolver
import com.vikash.ampli.drive.voice.WelcomeGreeter
import com.vikash.ampli.drive.system.AppRole
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.system.Shell
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

    /**
     * Also a boot: the first Home frame of a fresh process while the unit has been up less than
     * five minutes (units whose BOOT_COUNT never changes, or whose boot broadcast never comes).
     */
    fun isNewBoot(lastBootCount: Int, bootCount: Int, uptimeMs: Long, firstInProcess: Boolean): Boolean =
        isNewBoot(lastBootCount, bootCount, uptimeMs) || (firstInProcess && uptimeMs < FRESH_BOOT_UPTIME_MS)

    /** ACC off on many units is deep sleep, not shutdown: no boot broadcast comes at ACC on. */
    const val SLEEP_WAKE_MS = 3 * 60_000L

    /** True when the unit slept at least [SLEEP_WAKE_MS] since the last check (elapsed minus uptime). */
    fun wokeFromSleep(lastSleptMs: Long, sleptMs: Long): Boolean = lastSleptMs >= 0 && sleptMs - lastSleptMs >= SLEEP_WAKE_MS

    /** Screen off this long, then on again: the car was switched off and on. */
    fun screenOffWasStart(offAtElapsed: Long, onAtElapsed: Long): Boolean = offAtElapsed > 0 && onAtElapsed - offAtElapsed >= SLEEP_WAKE_MS

    /** When something last ran: wall clock, elapsedRealtime and Settings.Global.BOOT_COUNT (-1 = unknown). */
    data class RunMark(val wallMs: Long, val elapsedMs: Long, val bootCount: Int) {
        fun encode(): String = "$wallMs,$elapsedMs,$bootCount"

        companion object {
            fun decode(s: String?): RunMark? {
                val p = s?.split(',') ?: return null
                if (p.size != 3) return null
                return RunMark(p[0].toLongOrNull() ?: return null, p[1].toLongOrNull() ?: return null, p[2].toIntOrNull() ?: return null)
            }
        }
    }

    /** Same boot as [last]? Null when it can't be told. */
    fun sameBoot(last: RunMark, now: RunMark): Boolean? = when {
        last.bootCount >= 0 && now.bootCount >= 0 -> last.bootCount == now.bootCount
        last.elapsedMs >= 0 && now.elapsedMs >= 0 && now.elapsedMs < last.elapsedMs -> false
        else -> null
    }

    /**
     * Whether to run again after a run at [last]. Unlike [shouldRun] this survives a wall clock
     * that resets on every power cut (units without a clock battery and no network time): a new
     * boot always runs, and within a boot the gap is measured on elapsedRealtime. A boot signal
     * never runs twice in the same boot.
     */
    fun shouldRun(last: RunMark?, now: RunMark, trigger: StartTrigger): Boolean {
        if (last == null) return true
        return when (sameBoot(last, now)) {
            false -> true
            true -> trigger != StartTrigger.Boot && now.elapsedMs - last.elapsedMs >= MIN_GAP_MS
            null -> {
                val wallClose = now.wallMs - last.wallMs in 0 until MIN_GAP_MS
                val elapsedClose = last.elapsedMs < 0 || now.elapsedMs - last.elapsedMs in 0 until MIN_GAP_MS
                !(wallClose && elapsedClose)
            }
        }
    }
}

/**
 * Boot, ignition (ACC on) and "my phone connected" all end up here. The actions run from
 * MainActivity, which is in front then (it is the HOME app): Android 10+ only lets a visible app
 * start other apps. A signal that arrives while Car Hub is hidden is kept as pending and runs the
 * next time the launcher shows.
 *
 * Actions: the welcome message (WelcomeGreeter), spoken paper reminders (PaperVoice) and Settings › When the car
 * starts (open the phone-mirroring app, or start music and open the map).
 */
object CarStart {
    private val _events = MutableSharedFlow<StartTrigger>(extraBufferCapacity = 4)
    val events: SharedFlow<StartTrigger> = _events.asSharedFlow()

    /** Set while MainActivity is resumed. */
    @Volatile var launcherVisible = false

    fun signal(app: DriveApp, trigger: StartTrigger, source: String = trigger.name) {
        pendingTrigger = trigger
        pendingSource = source
        if (launcherVisible && _events.subscriptionCount.value > 0 && _events.tryEmit(trigger)) {
            // Handled now; a stale pending flag would replay it on a later return to Home.
            app.prefs.carStartPending = false
            return
        }
        app.prefs.carStartPending = true
        if (trigger != StartTrigger.PhoneConnected) app.startLog.add(source, greeted = false, reason = "received; waiting for the Car Hub screen")
        if (trigger != StartTrigger.PhoneConnected && !DriveLock.movingNow(app.speedTracker.state.value.speedKmh)) {
            // Boot or ignition with another app (or another launcher) in front: bring Ampli forward,
            // but never over the map while the car is moving.
            // Android 8/9 head units allow it; 10+ may refuse a background start, and then the
            // pending flag runs everything the next time the launcher shows.
            try {
                app.startActivity(
                    Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            } catch (_: Exception) {
            }
        }
    }

    @Volatile private var pendingTrigger: StartTrigger = StartTrigger.Boot
    @Volatile private var pendingSource: String = StartTrigger.Boot.name

    /** False until the first onResume of this process (a fresh process soon after boot is a boot). */
    @Volatile private var resumedInProcess = false

    /** Deep-sleep total (elapsedRealtime minus uptimeMillis) at the last check; -1 = not yet. */
    @Volatile private var lastSleptMs = -1L

    /** Screen-off time (elapsedRealtime) seen by the drive monitor, for [onScreenOn]. */
    @Volatile private var screenOffAt = 0L

    /** Where a run was asked from, for the start log. */
    @Volatile private var runSource: String? = null

    private fun bootCount(context: Context): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (_: Exception) {
        -1
    }

    private fun mark(context: Context) = StartPolicy.RunMark(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount(context))

    /**
     * MainActivity.onResume: a reboot noticed by boot count or a fresh process soon after boot,
     * a wake from deep sleep (ACC on), or a pending signal.
     */
    fun takePending(context: Context, app: DriveApp): StartTrigger? {
        val prefs = app.prefs
        val bootCount = bootCount(context)
        val uptime = SystemClock.elapsedRealtime()
        val firstInProcess = !resumedInProcess
        resumedInProcess = true
        val countChanged = StartPolicy.isNewBoot(prefs.lastBootCount, bootCount, uptime)
        val newBoot = StartPolicy.isNewBoot(prefs.lastBootCount, bootCount, uptime, firstInProcess)
        if (bootCount >= 0) prefs.lastBootCount = bootCount
        val slept = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()
        val woke = StartPolicy.wokeFromSleep(lastSleptMs, slept)
        lastSleptMs = slept
        val pending = prefs.carStartPending
        if (!newBoot && !woke && !pending) return null
        prefs.carStartPending = false
        return when {
            newBoot -> {
                runSource = if (countChanged && bootCount >= 0) "boot (boot count $bootCount)" else "boot (first Home after start, up ${uptime / 1000}s)"
                StartTrigger.Boot
            }
            woke -> {
                runSource = "wake from sleep (ACC on)"
                StartTrigger.Ignition
            }
            else -> {
                runSource = pendingSource
                pendingTrigger
            }
        }
    }

    /** MainActivity.onStop: start counting deep sleep from here. */
    fun onLauncherHidden() {
        lastSleptMs = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()
    }

    /** Drive monitor: the screen went off (ignition off on most units). */
    fun onScreenOff() {
        screenOffAt = SystemClock.elapsedRealtime()
    }

    /** Drive monitor: the screen came back after a long off (or a deep sleep): the car started. */
    fun onScreenOn(app: DriveApp) {
        val now = SystemClock.elapsedRealtime()
        val slept = now - SystemClock.uptimeMillis()
        val woke = StartPolicy.screenOffWasStart(screenOffAt, now) || StartPolicy.wokeFromSleep(lastSleptMs, slept)
        screenOffAt = 0L
        lastSleptMs = slept
        if (woke) signal(app, StartTrigger.Ignition, "screen on after a long off")
    }

    /** Runs the greeting and the auto start. Call from the visible launcher (lifecycleScope). */
    suspend fun run(context: Context, app: DriveApp, trigger: StartTrigger) {
        val prefs = app.prefs
        val source = runSource ?: pendingSource.takeIf { pendingTrigger == trigger } ?: trigger.name
        runSource = null
        val now = mark(context)
        val settings = prefs.autoStart.value

        // Welcome audio: "Jai Baba Ki {name} Ji, aaj kit ka plan hai?" (Haryanvi) or a plain welcome (Standard).
        // Its own once-per-start record, so a phone connecting first can't use up the greeting.
        if (StartPolicy.greets(trigger)) {
            val last = StartPolicy.RunMark.decode(prefs.lastGreetMark)
            if (!StartPolicy.shouldRun(last, now, trigger)) {
                app.startLog.add(source, greeted = false, reason = "already greeted for this start")
            } else {
                prefs.lastGreetMark = now.encode()
                prefs.lastCarStartAt = now.wallMs
                // Ampli startup animation first (Settings › Startup animation), then "Jai Baba Ki…".
                // Never over the map while driving (an ACC or quick-boot event can arrive on the move,
                // and any app can send the quick-boot broadcast): then the greeting is voice only.
                val moving = DriveLock.movingNow(app.speedTracker.state.value.speedKmh)
                if (!moving && Shell.requestSplash(app.shell.startupAnimation.value)) delay(Shell.SPLASH_MS)
                val outcome = app.welcome.play(showCard = !moving)
                app.startLog.add(source, greeted = outcome == WelcomeGreeter.Outcome.Spoken, reason = outcome.reason)
                // Papers about to expire / FASTag pass running low, in the same voice (at most two).
                try {
                    app.paperVoice.speakAtStart()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }

        if (settings.mode == AutoStartMode.Off) return
        val lastAuto = StartPolicy.RunMark.decode(prefs.lastAutoStartMark)
        // A phone connecting shortly after boot is the same start: one auto start only.
        if (!StartPolicy.shouldRun(lastAuto, now, if (trigger == StartTrigger.PhoneConnected) StartTrigger.Ignition else trigger)) return
        prefs.lastAutoStartMark = now.encode()
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
        app.prefs.applyMusicDefault { AmpliMusic.installed(context) }
        val pkg = app.prefs.musicPackage.value
            ?: AmpliMusic.PACKAGE.takeIf { AmpliMusic.installed(context) }
            ?: AppResolver.guessMusicPackage(context)
        // Ampli opens in its Car mode; play is pressed below through its media session.
        if (pkg == AmpliMusic.PACKAGE) {
            AmpliMusic.open(context, autoplay = false)
        } else if (pkg != null) {
            Launchers.openPackage(context, pkg, quiet = true)
        }
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
 * BOOT_COMPLETED, LOCKED_BOOT_COMPLETED and the quick-boot twins head units send instead of a real
 * boot, plus vendor ACC-on broadcasts when they reach a manifest receiver. It only marks the start
 * as pending and brings Car Hub forward (Android 8/9 allow that); the actions run from the
 * visible launcher (see [CarStart]).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? DriveApp ?: return
        val action = intent.action ?: return
        val trigger = when (action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> StartTrigger.Boot
            else -> {
                // ACC off twins of the vendor actions must not greet.
                val event = IgnitionActions.classify(action) { key -> intent.getStringExtra(key) }
                if (event == IgnitionEvent.Off) return
                StartTrigger.Ignition
            }
        }
        CarStart.signal(app, trigger, "broadcast " + action.substringAfterLast('.'))
    }
}
