package com.vikash.ampli.drive.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Car Hub's music is Ampli MP3 Player (com.vikash.ampli). The Music button opens it straight into
 * its Car mode; when it is not on the unit, the "Get Ampli MP3 Player" sheet opens instead of some
 * other music app (see [AppResolver.openMusic]).
 */
object AmpliMusic {
    const val PACKAGE = "com.vikash.ampli"

    /** Ampli MP3 Player's Car mode (newer builds). Extra [EXTRA_AUTOPLAY]: resume or shuffle all. */
    const val ACTION_CAR_MODE = "com.vikash.ampli.action.CAR_MODE"
    const val EXTRA_AUTOPLAY = "autoplay"

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }

    /**
     * Opens the player: its Car mode when that build has it, else its normal start screen.
     * [autoplay] only when the driver tapped Music (not for silent background actions).
     */
    fun open(context: Context, autoplay: Boolean): Boolean {
        val car = Intent(ACTION_CAR_MODE)
            .setPackage(PACKAGE)
            .putExtra(EXTRA_AUTOPLAY, autoplay)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolves = try {
            car.resolveActivity(context.packageManager) != null
        } catch (_: Exception) {
            false
        }
        if (resolves) {
            try {
                context.startActivity(car)
                return true
            } catch (_: Exception) {
            }
        }
        // Builds up to 124 have no Car mode: the launcher entry.
        return Launchers.openPackage(context, PACKAGE, quiet = true)
    }

    private val _askInstall = MutableStateFlow(false)

    /** True while the "Get Ampli MP3 Player" sheet should show (DriveRoot hosts it). */
    val askInstall: StateFlow<Boolean> = _askInstall.asStateFlow()

    fun showInstallSheet() {
        _askInstall.value = true
    }

    fun hideInstallSheet() {
        _askInstall.value = false
    }
}

/** Plain rules for the music app, unit-tested. */
object MusicDefault {

    /**
     * One-time switch to Ampli MP3 Player. Older builds stored whatever app was guessed or picked
     * (often Spotify). Once Ampli is installed the stored app becomes Ampli, once; a choice the
     * driver makes after this version ([pickedSinceChange]) is kept. Returns the package to store,
     * or null to leave things as they are.
     */
    fun migrate(stored: String?, ampliInstalled: Boolean, alreadyMigrated: Boolean, pickedSinceChange: Boolean): String? = when {
        alreadyMigrated || pickedSinceChange || !ampliInstalled -> null
        stored == AmpliMusic.PACKAGE -> null
        else -> AmpliMusic.PACKAGE
    }

    /** True when [migrate] has nothing left to do (so it is not checked again). */
    fun migrationDone(ampliInstalled: Boolean, alreadyMigrated: Boolean, pickedSinceChange: Boolean): Boolean =
        alreadyMigrated || pickedSinceChange || ampliInstalled

    enum class Target { Picked, Ampli, GetAmpli }

    /** What the Music button does: the driver's own pick, Ampli MP3 Player, or the install sheet. */
    fun target(stored: String?, ampliInstalled: Boolean): Target = when {
        stored != null && stored != AmpliMusic.PACKAGE -> Target.Picked
        ampliInstalled -> Target.Ampli
        else -> Target.GetAmpli
    }
}
