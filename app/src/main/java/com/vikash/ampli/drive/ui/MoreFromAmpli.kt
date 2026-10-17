package com.vikash.ampli.drive.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.billing.ProBilling
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/**
 * One row in Settings › More from Ampli (in-app-hook-spec v1 §4). Edit [AMPLI_PRODUCTS] to add a
 * product or to flip [live] once it is on Play.
 */
data class AmpliProduct(
    @StringRes val name: Int,
    @StringRes val line: Int,
    /** Play package, or null while it has none yet. */
    val packageName: String?,
    /** On Play: "Get" opens its listing. Otherwise "Coming soon" opens ampliapps.in. */
    val live: Boolean,
    /** Logo image, or null for a [monogram] tile. */
    @DrawableRes val logo: Int?,
    val monogram: String = "",
) {
    /** market:// first, then the Play web page; ampliapps.in before launch. */
    val links: List<String>
        get() = if (live && packageName != null) {
            listOf("market://details?id=$packageName", "https://play.google.com/store/apps/details?id=$packageName")
        } else {
            listOf(WEBSITE_URL)
        }
}

val AMPLI_PRODUCTS = listOf(
    AmpliProduct(
        name = R.string.more_mp3_name,
        line = R.string.more_mp3_line,
        packageName = "com.vikash.ampli",
        live = false,
        logo = R.drawable.ampli_mp3_logo,
    ),
    AmpliProduct(
        name = R.string.more_naukri_name,
        line = R.string.more_naukri_line,
        packageName = null,
        live = false,
        logo = null,
        monogram = "N",
    ),
)

internal val Saffron = Color(0xFFFF9933)
internal val Indigo = Color(0xFF1E1B4B)

/** Settings › More from Ampli: the other Ampli apps, each with a button to Play or the website. */
@Composable
internal fun MoreFromAmpliCard(products: List<AmpliProduct> = AMPLI_PRODUCTS) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                Icon(Icons.Filled.Apps, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                CardTitle(stringResource(R.string.more_title), stringResource(R.string.more_hint))
            }
            for (p in products) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                        .heightIn(min = MinTouch)
                        .padding(horizontal = dims.d(1.8f), vertical = dims.d(1.2f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
                ) {
                    ProductLogo(p)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(p.name), color = theme.text, fontSize = dims.t(2.9f, min = 17f), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(p.line), color = theme.muted, fontSize = dims.t(2.3f, min = 13f))
                    }
                    BigButton(
                        text = stringResource(if (p.live) R.string.more_get else R.string.more_coming_soon),
                        onClick = { p.links.any { startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
                        primary = p.live,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductLogo(p: AmpliProduct) {
    val size = 56.dp
    val shape = RoundedCornerShape(14.dp)
    if (p.logo != null) {
        Image(painterResource(p.logo), contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(Modifier.size(size).clip(shape).background(Indigo), contentAlignment = Alignment.Center) {
            Text(p.monogram, color = Saffron, fontSize = LocalDims.current.t(4f, min = 26f), fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** Settings › Buy the developer a chai: Play Billing tips (unlock nothing), with a thank-you once paid. */
@Composable
internal fun SupportCard(app: DriveApp) {
    val context = LocalContext.current
    val prices by app.billing.tipPrices.collectAsStateWithLifecycle()
    val thanked by app.billing.tipsThanked.collectAsStateWithLifecycle()
    // Only thank for tips given while this screen is open, not for an older count.
    val seen = remember { mutableIntStateOf(thanked) }
    SupportCardContent(
        prices = prices,
        thanked = thanked > seen.intValue,
        onTip = { id ->
            val activity = context.findActivity()
            if (activity == null || !app.billing.tip(activity, id)) {
                Toast.makeText(context, context.getString(R.string.support_unavailable), Toast.LENGTH_LONG).show()
            }
        },
    )
}

@Composable
internal fun SupportCardContent(prices: Map<String, String>, thanked: Boolean, onTip: (String) -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    SettingCard {
        Column(
            Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                Icon(Icons.Filled.Coffee, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) { CardTitle(stringResource(R.string.support_title), stringResource(R.string.support_hint)) }
            }
            val tiers = ProBilling.TIP_IDS.mapNotNull { id -> prices[id]?.let { id to it } }
            if (tiers.isEmpty()) {
                // Sideloaded build, or the tips are not created in Play Console yet.
                Text(stringResource(R.string.support_unavailable), color = theme.muted, fontSize = dims.t(2.4f, min = 14f))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)), modifier = Modifier.fillMaxWidth()) {
                    tiers.forEachIndexed { i, (id, price) ->
                        BigButton(
                            text = price,
                            onClick = { onTip(id) },
                            icon = if (i == tiers.lastIndex) Icons.Filled.Favorite else Icons.Filled.Coffee,
                            primary = i == tiers.lastIndex,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = thanked,
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) +
                    scaleIn(spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.9f) +
                    fadeIn(tween(220)),
            ) {
                Text(
                    stringResource(R.string.support_thanks),
                    color = theme.text,
                    fontSize = dims.t(2.6f, min = 15f),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.accent.copy(alpha = 0.16f))
                        .border(1.dp, theme.accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = dims.d(2f), vertical = dims.d(1.4f)),
                )
            }
        }
    }
}
