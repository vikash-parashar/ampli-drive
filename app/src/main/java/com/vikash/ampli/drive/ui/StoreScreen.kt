package com.vikash.ampli.drive.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.BuildConfig
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.billing.ProPricing
import com.vikash.ampli.drive.media.MediaHub
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/** Play listing of the Ampli MP3 Player: the Play app first, the web page when Play is missing. */
fun mp3PlayerLinks(packageName: String = MediaHub.AMPLI_PACKAGE): List<String> = listOf(
    "market://details?id=$packageName",
    "https://play.google.com/store/apps/details?id=$packageName",
)

/** Opens the MP3 Player when installed, else its Play listing. */
fun openOrInstallMp3(context: Context, installed: Boolean) {
    if (installed) {
        context.packageManager.getLaunchIntentForPackage(MediaHub.AMPLI_PACKAGE)?.let { if (startSafely(context, it)) return }
    }
    for (link in mp3PlayerLinks()) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
    android.widget.Toast.makeText(context, context.getString(R.string.error_cannot_open_app), android.widget.Toast.LENGTH_SHORT).show()
}

/** Voice packs listed as "Coming soon" (₹99 each). */
private val VOICE_PACKS = listOf(R.string.store_voice_punjabi, R.string.store_voice_rajasthani, R.string.store_voice_bhojpuri)
private const val VOICE_PACK_PRICE = "₹99"

/** Apps › Ampli Store: Car Hub Pro, the MP3 Player and the coming voice packs, in one place. */
@Composable
fun StoreScreen(app: DriveApp, onBack: () -> Unit, onUnlockPro: () -> Unit) {
    val context = LocalContext.current
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val price by app.billing.price.collectAsStateWithLifecycle()
    val isLaunchPrice by app.billing.isLaunchPrice.collectAsStateWithLifecycle()
    val mp3Installed = remember { context.packageManager.getLaunchIntentForPackage(MediaHub.AMPLI_PACKAGE) != null }
    StoreContent(
        isPro = pro.isPro,
        proPrice = price ?: ProPricing.fallbackPrice(BuildConfig.LAUNCH_OFFER),
        launchLine = BuildConfig.LAUNCH_OFFER && isLaunchPrice,
        mp3Installed = mp3Installed,
        onBack = onBack,
        onPro = onUnlockPro,
        onMp3 = { openOrInstallMp3(context, mp3Installed) },
    )
}

/** The store page itself, without app state (screen previews use it directly). */
@Composable
internal fun StoreContent(
    isPro: Boolean,
    proPrice: String,
    launchLine: Boolean,
    mp3Installed: Boolean,
    onBack: () -> Unit,
    onPro: () -> Unit,
    onMp3: () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(stringResource(R.string.store_title), stringResource(R.string.store_hint), onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
        ) {
            StoreRow(
                logo = { IconTile(Icons.Filled.WorkspacePremium) },
                name = stringResource(R.string.store_pro_name),
                line = if (launchLine) {
                    stringResource(R.string.store_pro_line) + "\n" + stringResource(
                        R.string.pro_launch_line,
                        ProPricing.rupees(ProPricing.LAUNCH_PRICE_INR),
                        ProPricing.rupees(ProPricing.REGULAR_PRICE_INR),
                    )
                } else {
                    stringResource(R.string.store_pro_line)
                },
            ) {
                if (isPro) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = theme.ok, modifier = Modifier.size(26.dp))
                        Text(stringResource(R.string.store_owned), color = theme.ok, fontSize = dims.t(2.6f, min = 16f), fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    BigButton(proPrice, onPro)
                }
            }
            StoreRow(
                logo = { Image(painterResource(R.drawable.ampli_mp3_logo), contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))) },
                name = stringResource(R.string.more_mp3_name),
                line = stringResource(R.string.more_mp3_line),
            ) {
                BigButton(stringResource(if (mp3Installed) R.string.action_open else R.string.store_install), onMp3, primary = !mp3Installed)
            }
            for (pack in VOICE_PACKS) {
                StoreRow(
                    logo = { IconTile(Icons.Filled.RecordVoiceOver) },
                    name = stringResource(pack),
                    line = stringResource(R.string.store_voice_line) + " · " + VOICE_PACK_PRICE,
                ) {
                    Text(stringResource(R.string.more_coming_soon), color = theme.muted, fontSize = dims.t(2.6f, min = 16f), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Indigo), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Saffron, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun StoreRow(logo: @Composable () -> Unit, name: String, line: String, action: @Composable () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    SettingCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MinTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            logo()
            Column(Modifier.weight(1f)) {
                Text(name, color = theme.text, fontSize = dims.t(3f, min = 18f), fontWeight = FontWeight.SemiBold)
                Text(line, color = theme.muted, fontSize = dims.t(2.3f, min = 13f))
            }
            action()
        }
    }
}
