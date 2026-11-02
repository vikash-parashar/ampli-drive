package com.vikash.ampli.drive.system

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One app on the Cleaner screen. */
data class CleanerApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    /** App + data + cache with usage access; otherwise only the APK files ([sizeExact] false). */
    val sizeBytes: Long,
    val sizeExact: Boolean,
    /** Last time the driver used it (epoch ms), when usage access is on and Android knows. */
    val lastUsed: Long?,
    /** System apps can't be uninstalled: they get "Band karo" (App info) instead of "Hatao". */
    val system: Boolean,
)

/** RAM and storage for the Cleaner header. */
data class CleanerMemory(val ramTotal: Long, val ramUsed: Long, val storageFree: Long)

/**
 * Apps › "Saaf karo": the installed launchable apps, heaviest first, with when each was last used.
 * Sizes come from StorageStatsManager when usage access is allowed (Settings › Usage access),
 * otherwise from the APK files alone. Ampli itself is never listed, and system apps are never
 * offered for uninstall.
 */
object AppCleaner {

    /** Not opened for this long: shown in amber ("9 din se nahi khola"). */
    const val STALE_DAYS = 7L

    /** Heavier than this is worth a look even when used recently. */
    const val HEAVY_BYTES = 300L * 1024 * 1024

    /** Usage access (PACKAGE_USAGE_STATS) allowed for Car Hub. */
    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean = try {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }

    /** Settings › Usage access (Car Hub's own page where the unit has one). */
    fun openUsageAccess(context: Context): Boolean {
        val own = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName))
        return start(context, own) || start(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    /** Apps Ampli never lists: itself and its sister apps. */
    fun hidden(context: Context, packageName: String): Boolean =
        packageName == context.packageName || packageName.startsWith("com.vikash.ampli")

    @Suppress("DEPRECATION")
    suspend fun load(context: Context): List<CleanerApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launchable = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        } catch (_: Exception) {
            emptyList()
        }
        val access = hasUsageAccess(context)
        val lastUsed = if (access) lastUsedMap(context) else emptyMap()
        val stats = if (access) context.getSystemService(StorageStatsManager::class.java) else null
        launchable
            .asSequence()
            .filter { !hidden(context, it.activityInfo.packageName) }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { ri ->
                val ai = ri.activityInfo.applicationInfo ?: return@mapNotNull null
                val pkg = ri.activityInfo.packageName
                val exact = stats?.let { exactSize(it, ai) }
                CleanerApp(
                    packageName = pkg,
                    activityName = ri.activityInfo.name,
                    label = try {
                        ri.loadLabel(pm)?.toString() ?: pkg
                    } catch (_: Exception) {
                        pkg
                    },
                    sizeBytes = exact ?: apkSize(ai),
                    sizeExact = exact != null,
                    lastUsed = lastUsed[pkg]?.takeIf { it > 0L },
                    system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedWith(compareBy<CleanerApp> { it.system }.thenByDescending { it.sizeBytes })
            .toList()
    }

    /** App, data and cache bytes (needs usage access). */
    private fun exactSize(stats: StorageStatsManager, ai: ApplicationInfo): Long? = try {
        val s = stats.queryStatsForPackage(ai.storageUuid, ai.packageName, Process.myUserHandle())
        s.appBytes + s.dataBytes + s.cacheBytes
    } catch (_: Exception) {
        null
    }

    /** The APK and its splits: all that can be read without usage access. */
    private fun apkSize(ai: ApplicationInfo): Long {
        var total = 0L
        try {
            ai.sourceDir?.let { total += File(it).length() }
            ai.splitSourceDirs?.forEach { total += File(it).length() }
        } catch (_: Exception) {
        }
        return total
    }

    /** Package → last time used, over the last two months. */
    private fun lastUsedMap(context: Context): Map<String, Long> = try {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        usm?.queryAndAggregateUsageStats(now - 60L * 24 * 60 * 60 * 1000, now)
            ?.mapValues { it.value.lastTimeUsed }
            .orEmpty()
    } catch (_: Exception) {
        emptyMap()
    }

    /** Whole days since [lastUsed], or null when unknown. Pure. */
    fun daysSince(lastUsed: Long?, now: Long): Long? =
        lastUsed?.let { ((now - it).coerceAtLeast(0L)) / (24L * 60 * 60 * 1000) }

    /** Worth cleaning: not opened for [STALE_DAYS] days, or heavier than [HEAVY_BYTES]. Pure. */
    fun worthCleaning(app: CleanerApp, now: Long): Boolean {
        if (app.system) return false
        val days = daysSince(app.lastUsed, now)
        return (days != null && days >= STALE_DAYS) || app.sizeBytes >= HEAVY_BYTES
    }

    fun memory(context: Context): CleanerMemory {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        try {
            am?.getMemoryInfo(info)
        } catch (_: Exception) {
        }
        val free = try {
            StatFs(Environment.getDataDirectory().path).availableBytes
        } catch (_: Exception) {
            0L
        }
        return CleanerMemory(info.totalMem, (info.totalMem - info.availMem).coerceAtLeast(0L), free)
    }

    /** Android's "Uninstall this app?" (REQUEST_DELETE_PACKAGES). Never for system apps or Ampli. */
    @Suppress("DEPRECATION")
    fun uninstall(context: Context, app: CleanerApp): Boolean {
        if (app.system || hidden(context, app.packageName)) return false
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName))
        return start(context, intent) || start(context, Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + app.packageName)))
    }

    /** Android's App info page (Force stop, storage, uninstall or disable). */
    fun appInfo(context: Context, packageName: String): Boolean =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
