package com.vikash.ampli.drive.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.system.AppEntry
import com.vikash.ampli.drive.system.InstalledApps
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.ui.theme.Ampli

/** All launchable apps. Tap opens; long-press puts the app on the dock's Auto button. */
@Composable
fun AppsScreen(app: DriveApp, onBack: () -> Unit) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val autoPackage by app.prefs.autoPackage.collectAsStateWithLifecycle()
    val apps by produceState<List<AppEntry>?>(null) { value = InstalledApps.load(context) }

    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(2.4f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        ScreenHeader(
            title = "All apps",
            hint = "Long-press an app to put it on the Auto button",
            onBack = onBack,
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Ampli.Rose)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = dims.d(2f)),
                horizontalArrangement = Arrangement.spacedBy(dims.d(1.5f)),
                verticalArrangement = Arrangement.spacedBy(dims.d(1.5f)),
            ) {
                items(list, key = { it.packageName + "/" + it.activityName }) { entry ->
                    AppTile(
                        entry = entry,
                        isAuto = entry.packageName == autoPackage,
                        onOpen = { Launchers.openComponent(context, entry.packageName, entry.activityName) },
                        onLongPress = {
                            if (entry.packageName == autoPackage) {
                                app.prefs.setAutoPackage(null)
                                Toast.makeText(context, "Removed from the Auto button", Toast.LENGTH_SHORT).show()
                            } else {
                                app.prefs.setAutoPackage(entry.packageName)
                                Toast.makeText(context, "${entry.label} is now on the Auto button", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, hint: String?, onBack: () -> Unit) {
    val dims = LocalDims.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Box(
            modifier = Modifier
                .size(MinTouch)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = dims.t(4.2f, min = 22f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            if (hint != null) {
                Text(hint, fontSize = dims.t(2.3f), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AppTile(entry: AppEntry, isAuto: Boolean, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val iconPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, entry) { value = InstalledApps.icon(context, entry, iconPx) }
    val shape = RoundedCornerShape(dims.d(2.2f))
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (isAuto) Ampli.Rose else MaterialTheme.colorScheme.outlineVariant, shape)
            .pointerInput(entry) {
                detectTapGestures(onTap = { currentOnOpen() }, onLongPress = { currentOnLongPress() })
            }
            .padding(dims.d(1.4f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            val bmp = icon
            if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.size(56.dp))
            if (isAuto) {
                Icon(
                    Icons.Filled.DirectionsCar,
                    contentDescription = "On the Auto button",
                    tint = Ampli.Rose,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
        Text(
            entry.label,
            fontSize = dims.t(2.2f),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
