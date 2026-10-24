package com.vikash.ampli.drive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.voice.Place
import com.vikash.ampli.drive.voice.PlaceSearch
import com.vikash.ampli.drive.voice.VoiceActions
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * "Where to?": type a place (or tap the mic for "Hey Ampli"), pick one of the matches, and
 * Car Hub's own navigation starts. Opened by the dock's Navigate button and by tapping the map
 * when Settings › Navigate with is "Ampli map".
 */
@Composable
fun NavSearchScreen(app: DriveApp, onBack: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf("") }
    // "Save this place as" for a search result (its name) or the current location (no name).
    var saving by remember { mutableStateOf<Place?>(null) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        keyboard?.hide()
        searching = true
        failed = false
        searched = q
        scope.launch {
            results = try {
                PlaceSearch.search(q, app.speedTracker.state.value.location)
            } catch (_: Exception) {
                failed = true
                null
            }
            searching = false
        }
    }

    fun go(place: Place) {
        // DriveRoot switches to the navigation screen when the navigator asks.
        app.navigator.start(place)
    }

    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(1.8f))) {
        ScreenHeader(title = stringResource(R.string.nav_search_title), hint = stringResource(R.string.nav_search_hint), onBack = onBack)
        // While moving: no keyboard, only "speak your destination".
        val moving = isMoving(app)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
            if (moving) {
                Text(
                    stringResource(R.string.nav_search_moving),
                    fontSize = dims.t(2.8f, min = 16f),
                    color = LocalDriveTheme.current.warning,
                    modifier = Modifier.weight(1f),
                )
            } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(120) },
                label = { Text(stringResource(R.string.nav_search_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f),
            )
            BigButton(text = stringResource(R.string.nav_search_button), onClick = { search() }, icon = Icons.Filled.Search)
            }
            BigButton(
                text = stringResource(R.string.nav_search_speak),
                onClick = {
                    onBack()
                    app.voice.start()
                },
                icon = Icons.Filled.Mic,
                primary = false,
            )
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
        ) {
            // Home, Office and favourites: one tap starts the route.
            SavedPlacesSection(
                app = app,
                moving = moving,
                onSaveHere = {
                    val here = app.speedTracker.state.value.location
                    if (here == null) {
                        android.widget.Toast.makeText(context, context.getString(R.string.camera_report_no_fix), android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        saving = Place("", null, here.latitude, here.longitude, null)
                    }
                },
            )
            val list = results
            when {
                searching -> Box(Modifier.fillMaxWidth().padding(dims.d(3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.accent)
                }
                failed -> SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                        Text(stringResource(R.string.nav_search_failed), color = theme.text, fontSize = dims.t(2.8f, min = 16f), modifier = Modifier.weight(1f))
                        BigButton(text = stringResource(R.string.action_retry), onClick = { search() })
                        BigButton(
                            text = stringResource(R.string.nav_open_google),
                            onClick = { VoiceActions.startNavigationByName(context, searched) },
                            icon = Icons.Filled.Map,
                            primary = false,
                        )
                    }
                }
                list != null && list.isEmpty() -> Text(
                    stringResource(R.string.nav_search_none, searched),
                    color = theme.muted,
                    fontSize = dims.t(2.8f, min = 16f),
                )
                list != null -> list.forEach { place ->
                    PlaceRow(place, onGo = { go(place) }, onSave = if (moving) null else ({ saving = place }))
                }
            }
        }
    }
    saving?.let { p ->
        SavePlaceDialog(app = app, name = p.name.ifEmpty { null }, lat = p.lat, lon = p.lon, onDone = { saving = null })
    }
}

@Composable
private fun PlaceRow(place: Place, onGo: () -> Unit, onSave: (() -> Unit)? = null) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    SettingCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable(onClick = onGo),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(place.name, color = theme.text, fontSize = dims.t(3f, min = 18f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    place.area,
                    place.distanceKm?.let { String.format(Locale.getDefault(), "%.1f km", it) },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = theme.muted, fontSize = dims.t(2.4f, min = 14f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onSave != null) {
                BigButton(text = stringResource(R.string.place_save), onClick = onSave, icon = Icons.Filled.Star, primary = false)
            }
            BigButton(text = stringResource(R.string.nav_go), onClick = onGo)
        }
    }
}
