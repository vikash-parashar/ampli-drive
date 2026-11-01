package com.vikash.ampli.drive.voice

import android.net.ConnectivityManager
import android.net.Network
import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Gets the offline speech models onto the unit without the driver doing anything: on first start
 * (and every return home) the wanted models ([VoiceModelPlan.wanted]) are unpacked from the Play
 * asset pack or, on sideloaded APKs, downloaded on any network in the background. English goes
 * first (the wake word needs it), then Hindi, one at a time to spare 1-2 GB head units.
 * A network coming back retries a failed fetch straight away instead of waiting out the back-off.
 * A model the driver removed in Settings is left alone.
 */
class VoiceModels(private val app: DriveApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var followUp: Job? = null
    private var watching = false

    /** What this unit should have now (it follows Settings › Language). */
    fun want(): VoiceModelPlan.Want {
        val saved = app.prefs.language.value
        return VoiceModelPlan.wanted(
            savedLanguage = saved,
            parserLanguage = VoiceLocales.parserLanguage(saved),
            englishRemoved = app.wakeModel.removedByUser(),
            hindiRemoved = app.hindiModel.removedByUser(),
        )
    }

    /** Every wanted model is installed. */
    fun allReady(): Boolean {
        val w = want()
        return (!w.english || app.wakeModel.isInstalled()) && (!w.hindi || app.hindiModel.isInstalled())
    }

    /** First start and every return home: starts what is missing; cheap when all is in place. */
    fun start() {
        watchNetwork()
        ensureAll()
    }

    /**
     * Starts the next missing model (automatic rules). [networkBack]: a network just came up, so
     * the first few failures skip their back-off.
     */
    @Synchronized
    fun ensureAll(networkBack: Boolean = false) {
        val w = want()
        val en = app.wakeModel
        if (w.english && !en.isInstalled()) {
            en.ensure(automatic = true, ignoreBackoff = networkBack && VoiceModelPlan.retryOnNetwork(en.failures()))
            if (en.busy) {
                hindiAfterEnglish()
                return
            }
        }
        val hi = app.hindiModel
        if (w.hindi && !hi.isInstalled()) {
            hi.ensure(automatic = true, ignoreBackoff = networkBack && VoiceModelPlan.retryOnNetwork(hi.failures()))
        }
    }

    /** Hindi starts once the English job has finished (ready or failed). */
    private fun hindiAfterEnglish() {
        if (followUp?.isActive == true) return
        val en = app.wakeModel
        followUp = scope.launch {
            en.state.first { it == WakeModel.State.Ready || it is WakeModel.State.Failed || (it == WakeModel.State.Missing && !en.busy) }
            if (!en.busy) {
                val w = want()
                val hi = app.hindiModel
                if (w.hindi && !hi.isInstalled()) hi.ensure(automatic = true)
            }
        }
    }

    /** Retries as soon as any network comes up (also fires once now when one is up). */
    private fun watchNetwork() {
        if (watching) return
        watching = true
        try {
            app.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!allReady()) ensureAll(networkBack = true)
                }
            })
        } catch (_: Exception) {
            // Some ROMs refuse callbacks (SecurityException); app start and onResume still retry.
        }
    }
}
