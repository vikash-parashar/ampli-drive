package com.vikash.ampli.drive.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.nav.SavedKind
import com.vikash.ampli.drive.nav.SavedPlace
import com.vikash.ampli.drive.prefs.NavApp
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.voice.VoiceActions
import java.util.Locale

/** "Home", "Office" or the favourite's own name. */
fun savedPlaceLabel(context: Context, p: SavedPlace): String = when (p.kind) {
    SavedKind.Home -> context.getString(R.string.place_home)
    SavedKind.Office -> context.getString(R.string.place_office)
    SavedKind.Favourite -> p.name
}

private fun savedIcon(kind: SavedKind): ImageVector = when (kind) {
    SavedKind.Home -> Icons.Filled.Home
    SavedKind.Office -> Icons.Filled.Work
    SavedKind.Favourite -> Icons.Filled.Star
}

/**
 * Starts navigation to [p] the way a search result does: Car Hub's own map, or Google Maps when
 * Settings › Navigate with says so.
 */
fun goToSavedPlace(app: DriveApp, context: Context, p: SavedPlace) {
    val place = p.toPlace(savedPlaceLabel(context, p))
    if (app.prefs.navApp.value == NavApp.Ampli) {
        app.navigator.start(place)
    } else if (!VoiceActions.startNavigation(context, place)) {
        Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
    }
}

/**
 * One-tap chips for the saved places (Home first, then Office, then favourites). Nothing shows
 * when none is saved. [onLongPress] (Where to? only) offers to remove one.
 */
@Composable
fun SavedPlaceChips(
    app: DriveApp,
    modifier: Modifier = Modifier,
    onLongPress: ((SavedPlace) -> Unit)? = null,
    /** Map First: Home is its own "Go home" key in the turn card, so the chip would repeat it. */
    skipHome: Boolean = false,
) {
    val context = LocalContext.current
    val dims = LocalDims.current
    val all by app.places.places.collectAsStateWithLifecycle()
    val places = if (skipHome) all.filter { it.kind != SavedKind.Home } else all
    if (places.isEmpty()) return
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (p in places) {
            PlaceChip(
                text = savedPlaceLabel(context, p),
                icon = savedIcon(p.kind),
                onClick = { goToSavedPlace(app, context, p) },
                onLongClick = onLongPress?.let { { it(p) } },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceChip(text: String, icon: ImageVector, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = if (theme.radius <= 0f) RectangleShape else CircleShape
    // Glass themes: the stronger tint, since chips often sit on the map (like Pill).
    val bg = theme.overMap
    Row(
        Modifier
            .heightIn(min = MinTouch)
            .themedTap(shape, bg, theme.accent.copy(alpha = 0.6f), onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = dims.d(2f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
    ) {
        Icon(icon, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(24.dp))
        Text(
            text,
            color = theme.text,
            fontSize = dims.t(2.6f, min = 16f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 2.dp),
        )
    }
}

/**
 * "Save this place as": Home, Office or a favourite (named). [name] is the search result's name,
 * or null for the current location, which then needs a name for a favourite.
 */
@Composable
fun SavePlaceDialog(app: DriveApp, name: String?, lat: Double, lon: Double, onDone: () -> Unit) {
    val context = LocalContext.current
    val (bg, fg) = dialogColors()
    var text by remember { mutableStateOf(name.orEmpty()) }
    fun save(kind: SavedKind) {
        val n = text.trim().ifEmpty { String.format(Locale.US, "%.4f, %.4f", lat, lon) }
        val ok = app.places.save(SavedPlace(kind, n, lat, lon))
        val label = if (kind == SavedKind.Favourite) n else context.getString(if (kind == SavedKind.Home) R.string.place_home else R.string.place_office)
        Toast.makeText(
            context,
            if (ok) context.getString(R.string.place_saved, label) else context.getString(R.string.place_full, com.vikash.ampli.drive.nav.SavedPlacesLogic.MAX_FAVOURITES),
            Toast.LENGTH_SHORT,
        ).show()
        onDone()
    }
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = { Text(stringResource(R.string.place_save_as), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    label = { Text(stringResource(R.string.place_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BigButton(stringResource(R.string.place_home), { save(SavedKind.Home) }, Modifier.weight(1f), icon = Icons.Filled.Home)
                    BigButton(stringResource(R.string.place_office), { save(SavedKind.Office) }, Modifier.weight(1f), icon = Icons.Filled.Work)
                }
                BigButton(
                    stringResource(R.string.place_add_favourite),
                    { save(SavedKind.Favourite) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Star,
                    primary = false,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Hold on a chip in Where to?: remove that saved place. */
@Composable
fun RemovePlaceDialog(app: DriveApp, place: SavedPlace, onDone: () -> Unit) {
    val context = LocalContext.current
    val (bg, fg) = dialogColors()
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = { Text(savedPlaceLabel(context, place), fontWeight = FontWeight.Bold) },
        text = { Text(place.name + "\n" + String.format(Locale.US, "%.5f, %.5f", place.lat, place.lon)) },
        confirmButton = {
            TextButton(onClick = { app.places.remove(place); onDone() }) { Text(stringResource(R.string.action_remove)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Where to?: the saved places on top, with a hint, so they can be tapped or removed. */
@Composable
internal fun SavedPlacesSection(app: DriveApp, moving: Boolean, onSaveHere: (() -> Unit)?) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val places by app.places.places.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<SavedPlace?>(null) }
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(28.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(
                        stringResource(R.string.places_title),
                        stringResource(if (places.isEmpty()) R.string.places_empty else R.string.places_hint),
                    )
                }
                if (onSaveHere != null && !moving) {
                    BigButton(stringResource(R.string.place_save_here), onSaveHere, primary = false)
                }
            }
            SavedPlaceChips(app, Modifier.fillMaxWidth(), onLongPress = if (moving) null else ({ removing = it }))
        }
    }
    removing?.let { RemovePlaceDialog(app, it) { removing = null } }
}
