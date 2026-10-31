package com.vikash.ampli.drive.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.nav.SavedKind
import com.vikash.ampli.drive.nav.SavedPlace
import com.vikash.ampli.drive.system.MemoryTrim
import com.vikash.ampli.drive.ui.theme.CrimsonPalette
import com.vikash.ampli.drive.ui.theme.DriveKeys
import com.vikash.ampli.drive.ui.theme.DriveTheme
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Crimson Drive: the filled crimson / green keys and the car on Home.
 *
 * The car picture is decoded once, at the pane's size (inSampleSize, then density scaling during
 * the decode) and in RGB_565: about 1 MB at 1280x800 instead of 3.6 MB for the full ARGB picture.
 * No blur, no shader; the bottom fade is one gradient rect. While the car shows, the live map is
 * not composed at all, which is lighter than the map pane it replaces.
 */

/**
 * The crimson primary key (play, "Go", a screen's main button): a soft raised key filled with the
 * crimson gradient and a bright crimson edge (3:1 or more on the charcoal). Content goes on it in
 * [DriveKeys.onPrimary].
 */
@Composable
fun Modifier.primaryKey(theme: DriveTheme, keys: DriveKeys, shape: Shape, onClick: () -> Unit, role: Role? = Role.Button): Modifier =
    softClickable(theme, shape, onClick, role = role, edge = false, fill = keys.primary)
        .background(keys.primaryBrush, shape)
        .border(1.dp, keys.primaryEdge, shape)

/** The green Hey Ampli key, with its brighter green edge. Content goes on it in [DriveKeys.onVoice]. */
@Composable
fun Modifier.voiceKey(
    theme: DriveTheme,
    keys: DriveKeys,
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier =
    softClickable(theme, shape, onClick, edge = false, fill = keys.voice, onLongClick = onLongClick)
        .background(keys.voice, shape)
        .border(1.5.dp, keys.voiceEdge, shape)

/** The car picture, decoded at screen size and kept for the next Home. Plain sizes are unit-tested. */
object CarHeroImage {

    /** Decode size for a [srcW]x[srcH] picture shown "cover" in a [boxW]x[boxH] box. */
    data class Plan(val sample: Int, val width: Int, val height: Int)

    fun plan(srcW: Int, srcH: Int, boxW: Int, boxH: Int): Plan {
        if (srcW <= 0 || srcH <= 0 || boxW <= 0 || boxH <= 0) return Plan(1, srcW, srcH)
        // Cover: the picture is scaled until it fills the box in both directions; never up.
        val scale = minOf(1.0, max(boxW.toDouble() / srcW, boxH.toDouble() / srcH))
        val w = (srcW * scale).roundToInt().coerceAtLeast(1)
        val h = (srcH * scale).roundToInt().coerceAtLeast(1)
        var sample = 1
        while (srcW / (sample * 2) >= w && srcH / (sample * 2) >= h) sample *= 2
        return Plan(sample, w, h)
    }

    private class Entry(val boxW: Int, val boxH: Int, val image: ImageBitmap)

    @Volatile private var entry: Entry? = null

    init {
        // Launcher in the background: let the picture go; it is decoded again on the next Home.
        MemoryTrim.add { entry = null }
    }

    /** True once a picture is decoded and kept (screenshot tests wait for it). */
    val loaded: Boolean get() = entry != null

    /** The picture already decoded for this box size, or null. */
    fun cached(boxW: Int, boxH: Int): ImageBitmap? = entry?.takeIf { it.boxW == boxW && it.boxH == boxH }?.image

    /** Decodes the picture for a [boxW]x[boxH] box (call off the main thread), or null if it fails. */
    fun load(context: Context, boxW: Int, boxH: Int): ImageBitmap? {
        cached(boxW, boxH)?.let { return it }
        return try {
            val res = context.resources
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(res, R.drawable.car_hero, bounds)
            val p = plan(bounds.outWidth, bounds.outHeight, boxW, boxH)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = p.sample
                inPreferredConfig = Bitmap.Config.RGB_565
                val sampledW = bounds.outWidth / p.sample
                if (p.width in 1 until sampledW) {
                    inScaled = true
                    inDensity = sampledW
                    inTargetDensity = p.width
                } else {
                    inScaled = false
                }
            }
            val bmp = BitmapFactory.decodeResource(res, R.drawable.car_hero, opts) ?: return null
            bmp.asImageBitmap().also { entry = Entry(boxW, boxH, it) }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }
}

/** "Good evening!" + "Vikash" → "Good evening, Vikash"; without a name the greeting as it is. */
fun heroGreeting(hello: String, name: String?): String {
    val n = name?.trim().orEmpty()
    if (n.isEmpty()) return hello
    return hello.trimEnd('!', '।', '！', ' ') + ", " + n
}

/**
 * Home's map pane while parked (Crimson Drive): the green car (cover crop, bottom fade), a greeting
 * and one crimson key: "Go home" when Home is saved, else "Where to?". Tapping the picture or
 * "Show map" switches to the live map; starting a route does too (HomeScreen).
 */
@Composable
fun CarHeroPane(
    app: DriveApp,
    onGoHome: (SavedPlace) -> Unit,
    onWhereTo: () -> Unit,
    onShowMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDriveTheme.current
    val keys = theme.keys ?: return
    val dims = LocalDims.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val shape = theme.cardShape(dims)
    val places by app.places.places.collectAsStateWithLifecycle()
    val home = places.firstOrNull { it.kind == SavedKind.Home }
    val name by app.prefs.ownerName.collectAsStateWithLifecycle()
    val hour = LocalTime.now().hour
    val hello = stringResource(
        when (hour) {
            in 4..11 -> R.string.voice_greet_morning
            in 12..16 -> R.string.voice_greet_afternoon
            else -> R.string.voice_greet_evening
        },
    )
    val showMap = stringResource(R.string.hero_show_map)

    BoxWithConstraints(
        modifier
            .softSurface(theme, shape, SoftDepth.Panel)
            .clip(shape)
            .background(Color(0xFF050A07))
            .clickable(onClickLabel = showMap, role = Role.Button, onClick = onShowMap),
    ) {
        val boxW = with(density) { maxWidth.roundToPx() }
        val boxH = with(density) { maxHeight.roundToPx() }
        val image by produceState(CarHeroImage.cached(boxW, boxH), boxW, boxH) {
            if (value == null) value = withContext(Dispatchers.IO) { CarHeroImage.load(context.applicationContext, boxW, boxH) }
        }
        image?.let {
            Image(
                it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // CSS object-position 40% 60%: the car's nose and wheels stay in view on narrow panes.
                alignment = BiasAlignment(-0.2f, 0.2f),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(CrimsonPalette.HERO_FADE))),
        )
        Pill(
            text = showMap,
            icon = Icons.Filled.Map,
            onClick = onShowMap,
            modifier = Modifier.align(Alignment.TopEnd).padding(dims.d(1.6f)),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(dims.d(2.4f)),
        ) {
            Text(
                heroGreeting(hello, name),
                color = theme.text,
                fontSize = dims.t(3.6f, min = 20f),
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.hero_ready),
                color = theme.accentSoft,
                fontSize = dims.t(2.4f, min = 16f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(dims.d(1.6f)))
            if (home != null) {
                HeroKey(stringResource(R.string.hero_go_home), Icons.Filled.Home, keys) { onGoHome(home) }
            } else {
                HeroKey(stringResource(R.string.nav_search_title), Icons.Filled.Search, keys, onWhereTo)
            }
        }
    }
}

@Composable
private fun HeroKey(text: String, icon: ImageVector, keys: DriveKeys, onClick: () -> Unit) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val shape = androidx.compose.foundation.shape.CircleShape
    Row(
        Modifier
            .heightIn(min = maxOf(MinTouch, dims.d(8f)))
            .primaryKey(theme, keys, shape, onClick)
            .padding(horizontal = dims.d(3f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
    ) {
        Icon(icon, contentDescription = null, tint = keys.onPrimary, modifier = Modifier.size(26.dp))
        Text(
            text,
            color = keys.onPrimary,
            fontSize = dims.t(2.8f, min = 17f),
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
