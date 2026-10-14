package com.vikash.ampli.drive.emergency

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.widget.Toast
import com.vikash.ampli.drive.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** An Indian helpline shown on the Help panel. */
data class Helpline(val number: String, val labelRes: Int)

/**
 * SOS and helpline actions. Nothing here sends or calls on its own:
 * - Calls use ACTION_DIAL, so the head unit's Bluetooth dialer (or the phone) shows the number
 *   and the driver presses Call. No CALL_PHONE permission.
 * - SOS opens the SMS composer (ACTION_SENDTO "smsto:") with every contact and the message
 *   filled in, plus a share sheet for WhatsApp and others. No SEND_SMS permission.
 *
 * Future: sending SOS fully automatically (no tap) needs a small backend and a DLT-registered
 * SMS provider (TRAI rules for application-to-person SMS in India), or SEND_SMS, which Play
 * only allows for default SMS apps. Until then the driver or a passenger taps Send.
 */
object Sos {
    val HELPLINES = listOf(
        Helpline("112", R.string.help_112),
        Helpline("100", R.string.help_100),
        Helpline("108", R.string.help_108),
        Helpline("101", R.string.help_101),
        Helpline("1033", R.string.help_1033),
        Helpline("1091", R.string.help_1091),
        Helpline("1073", R.string.help_1073),
    )

    /** Opens the dialer with [number] filled in. */
    fun dial(context: Context, number: String): Boolean =
        start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    /** Opens the SMS app with all [numbers] as recipients and [body] typed in. */
    fun openSmsComposer(context: Context, numbers: List<String>, body: String): Boolean {
        val to = numbers.joinToString(";") { it.filter { c -> c.isDigit() || c == '+' } }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(to, ";+")))
            .putExtra("sms_body", body)
            .putExtra(Intent.EXTRA_TEXT, body)
        return start(context, intent)
    }

    /** Share sheet (WhatsApp, Telegram, email…). */
    fun share(context: Context, body: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
        return start(context, Intent.createChooser(send, context.getString(R.string.sos_share_title)))
    }

    /** Best last-known position from any provider, or [fallback] (the speedometer's fix) if newer. */
    @SuppressLint("MissingPermission")
    fun lastKnownLocation(context: Context, fallback: Location?): Location? {
        val lm = context.getSystemService(LocationManager::class.java)
        val candidates = mutableListOf<Location>()
        fallback?.let { candidates += it }
        if (lm != null && hasLocationPermission(context)) {
            for (provider in lm.allProviders) {
                try {
                    lm.getLastKnownLocation(provider)?.let { candidates += it }
                } catch (_: Exception) {
                }
            }
        }
        return candidates.maxByOrNull { it.time }
    }

    /**
     * Asks GPS (then network) for a fresh fix while the SOS countdown runs.
     * Returns a signal to cancel the request; [onFix] may never be called.
     */
    @SuppressLint("MissingPermission")
    fun requestFreshLocation(context: Context, onFix: (Location) -> Unit): CancellationSignal {
        val signal = CancellationSignal()
        val lm = context.getSystemService(LocationManager::class.java) ?: return signal
        if (!hasLocationPermission(context)) return signal
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (provider in lm.allProviders && lm.isProviderEnabled(provider)) {
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc -> if (loc != null) onFix(loc) }
                }
            } catch (_: Exception) {
            }
        }
        return signal
    }

    /**
     * "SOS from Vikash. I need help. Location: https://maps.google.com/?q=18.5,73.8
     * (accuracy ±12 m, 10:32). Vehicle: MH12AB1234 Swift. Blood group: B+. Notes: …"
     * Written in English so any contact, hospital or police control room can read it.
     */
    fun buildMessage(profile: EmergencyProfile, location: Location?, now: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder()
        sb.append(if (profile.ownerName.isNotBlank()) "SOS from ${profile.ownerName.trim()}. " else "SOS. ")
        sb.append("I need help. ")
        if (location != null) {
            val lat = "%.6f".format(Locale.US, location.latitude)
            val lng = "%.6f".format(Locale.US, location.longitude)
            val time = DateTimeFormatter.ofPattern("HH:mm, d MMM", Locale.US)
                .format(Instant.ofEpochMilli(location.time.takeIf { it > 0 } ?: now).atZone(ZoneId.systemDefault()))
            sb.append("Location: https://maps.google.com/?q=$lat,$lng (")
            if (location.hasAccuracy()) sb.append("accuracy ±${location.accuracy.roundToInt()}m, ")
            sb.append("$time). ")
        } else {
            sb.append("Location: not available (no GPS fix). ")
        }
        val vehicle = listOf(profile.vehicleNumber, profile.vehicleModel).filter { it.isNotBlank() }.joinToString(" ")
        if (vehicle.isNotBlank()) sb.append("Vehicle: ${vehicle.trim()}. ")
        if (profile.bloodGroup.isNotBlank()) sb.append("Blood group: ${profile.bloodGroup.trim()}. ")
        if (profile.medicalNotes.isNotBlank()) sb.append("Notes: ${profile.medicalNotes.trim()}. ")
        if (profile.insuranceNote.isNotBlank()) sb.append("Insurance: ${profile.insuranceNote.trim()}. ")
        return sb.toString().trim()
    }

    private fun hasLocationPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
        false
    }
}
