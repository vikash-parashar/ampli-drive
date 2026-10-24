package com.vikash.ampli.drive.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val packageName: String, val activityName: String, val label: String)

/** Launchable apps for the all-apps grid. Icons load lazily and stay in a small cache. */
object InstalledApps {
    private val iconCache = LruCache<String, ImageBitmap>(96)

    /** Drops the icon cache (MemoryTrim, when the launcher is hidden or memory is low). */
    fun clearCache() = iconCache.evictAll()

    @Suppress("DEPRECATION")
    suspend fun load(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name,
                    label = it.loadLabel(pm)?.toString() ?: it.activityInfo.packageName,
                )
            }
            .distinctBy { it.packageName + "/" + it.activityName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    suspend fun icon(context: Context, app: AppEntry, sizePx: Int): ImageBitmap? {
        val key = app.packageName + "/" + app.activityName
        iconCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName))
                val bmp = drawable.toBitmap(sizePx, sizePx).asImageBitmap()
                iconCache.put(key, bmp)
                bmp
            } catch (_: Exception) {
                null
            }
        }
    }
}
