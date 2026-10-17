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

/** One card in Settings › More from Ampli. Edit [AMPLI_PRODUCTS] to add or change products. */
data class AmpliProduct(
    @StringRes val name: Int,
    @StringRes val line: Int,
    /** Logo image, or null for a [monogram] tile. */
    @DrawableRes val logo: Int?,
    val monogram: String = "",
    /** Tried first (Play's market:// link), then [fallbackUrl]. */
    val url: String,
    val fallbackUrl: String? = null,
    val comingSoon: Boolean = false,
)

val AMPLI_PRODUCTS = listOf(
    AmpliProduct(
        name = R.string.more_mp3_name,
        line = R.string.more_mp3_line,
        logo = R.drawable.ampli_mp3_logo,
        url = "market://details?id=com.vikash.ampli",
        fallbackUrl = "https://play.google.com/store/apps/details?id=com.vikash.ampli",
    ),
    AmpliProduct(
        name = R.string.more_naukri_name,
        line = R.string.more_naukri_line,
        logo = null,
        monogram = "N",
        url = "https://ampliapps.in",
        comingSoon = true,
    ),
)

private val Saffron = Color(0xFFFF9933)
private val Indigo = Color(0xFF1E1B4B)

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
                        .background(theme.surfaceHigh)
                        .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                        .heightIn(min = MinTouch)
                        .padding(horizontal = dims.d(1.8f), vertical = dims.d(1.2f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
                ) {
                    ProductLogo(p)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                            Text(stringResource(p.name), color = theme.text, fontSize = dims.t(2.9f, min = 17f), fontWeight = FontWeight.SemiBold)
                            if (p.comingSoon) {
                                Text(
                                    stringResource(R.string.more_coming_soon),
                                    color = Indigo,
                                    fontSize = dims.t(2f, min = 12f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Saffron)
                                        .padding(horizontal = 10.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(stringResource(p.line), color = theme.muted, fontSize = dims.t(2.3f, min = 13f))
                    }
                    BigButton(
                        text = stringResource(if (p.comingSoon) R.string.more_learn_more else R.string.more_open_play),
                        onClick = {
                            if (!startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(p.url)))) {
                                p.fallbackUrl?.let { startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                            }
                        },
                        primary = false,
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

/** Settings › Help the developer grow: Play Billing tips, with a thank-you once paid. */
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
            val small = prices[ProBilling.TIP_SMALL]
            val big = prices[ProBilling.TIP_BIG]
            if (small == null && big == null) {
                // Sideloaded build, or the tips are not created in Play Console yet.
                Text(stringResource(R.string.support_unavailable), color = theme.muted, fontSize = dims.t(2.4f, min = 14f))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)), modifier = Modifier.fillMaxWidth()) {
                    if (small != null) {
                        BigButton(
                            text = stringResource(R.string.support_tip_small, small),
                            onClick = { onTip(ProBilling.TIP_SMALL) },
                            icon = Icons.Filled.Coffee,
                            primary = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (big != null) {
                        BigButton(
                            text = stringResource(R.string.support_tip_big, big),
                            onClick = { onTip(ProBilling.TIP_BIG) },
                            icon = Icons.Filled.Favorite,
                            primary = true,
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
