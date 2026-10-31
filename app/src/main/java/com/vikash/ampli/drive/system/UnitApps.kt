package com.vikash.ampli.drive.system

import android.content.Context
import android.content.pm.PackageManager
import java.util.Locale

/**
 * Settings › Diagnostics › Unit apps: the head unit's own apps that matter to Car Hub (Bluetooth,
 * phone link, music, radio, boot animation, factory settings), so a photo of the list tells us
 * the vendor's package names. Plain rules in [kind], unit-tested.
 */
object UnitApps {
    data class Row(val kind: String, val label: String, val packageName: String)

    private val RULES: List<Pair<String, List<String>>> = listOf(
        "Bluetooth" to listOf("bluetooth", "蓝牙", "btphone", "btmusic", "bt phone", "bt music"),
        "Phone link" to listOf("phonelink", "phone link", "carlink", "carplay", "zlink", "autokit", "mirror", "easyconn", "android auto", "projection", "手机互联"),
        "Music" to listOf("music", "media", "audio", "mp3", "player", "音乐"),
        "Radio" to listOf("radio", "fm", "收音"),
        "Boot animation" to listOf("bootanim", "boot animation", "bootlogo", "logo"),
        "Factory / settings" to listOf("factory", "carsetting", "car setting", "canbus", "mcu", "setting", "设置", "工厂"),
        "Theme / EQ / AUX" to listOf("theme", "equalizer", " eq", "dsp", "aux", "dofun"),
    )

    /** What kind of unit app [label]/[packageName] looks like, or null. */
    fun kind(label: String, packageName: String): String? {
        val l = " " + label.lowercase(Locale.ROOT) + " "
        val p = packageName.lowercase(Locale.ROOT)
        val segs = p.split('.')
        // "bt" as a package segment (com.syu.bt) or the whole label ("BT").
        if (l.trim() == "bt" || segs.drop(1).any { it == "bt" || it.startsWith("bt") }) return "Bluetooth"
        for ((kind, words) in RULES) {
            if (words.any { l.contains(it) || p.contains(it.trim().replace(" ", "")) }) return kind
        }
        if (l.trim() == "eq") return "Theme / EQ / AUX"
        return null
    }

    /** Installed apps that match, sorted by kind then label. Background thread. */
    @Suppress("DEPRECATION")
    fun list(context: Context): List<Row> = try {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .mapNotNull { info ->
                val label = try {
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    info.packageName
                }
                kind(label, info.packageName)?.let { Row(it, label, info.packageName) }
            }
            .filterNot { it.packageName.startsWith("com.google.android.") && it.kind != "Phone link" }
            .sortedWith(compareBy<Row> { it.kind }.thenBy { it.label.lowercase() })
            .take(80)
            .toList()
    } catch (_: Exception) {
        emptyList()
    }
}
