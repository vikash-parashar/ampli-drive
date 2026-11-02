package com.vikash.ampli.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one tile layout every quick-panel tile uses, so icons, titles and sub-lines line up from
 * tile to tile whatever the screen (1280x800, 1024x600) or theme: fixed padding, the icon
 * top-left in a fixed [TileMetrics.ICON] box, the title right under it, an optional one-line
 * sub-line with an ellipsis, the whole group centred vertically. The sub-line is dropped when the
 * tile is too short for it, so nothing is ever cut in half; [TileMetrics.minHeight] gives the row
 * height that always fits title and sub-line.
 */
object TileMetrics {
    val PAD_H: Dp = 14.dp
    val PAD_V: Dp = 10.dp
    val ICON: Dp = 30.dp
    val GAP: Dp = 4.dp

    /** Line height for a font size: tight, with font padding off, so the sums below hold. */
    const val LINE = 1.2f

    @Composable
    fun titleSize(): TextUnit = LocalDims.current.t(3f, min = 18f)

    @Composable
    fun subSize(): TextUnit = LocalDims.current.t(2.25f, min = 14f)

    /** The height the content needs: icon, title and (when [withSub]) the sub-line. */
    @Composable
    fun contentHeight(withSub: Boolean): Dp {
        val density = LocalDensity.current
        val title = with(density) { (titleSize().value * LINE).sp.toDp() }
        val sub = with(density) { (subSize().value * LINE).sp.toDp() }
        return PAD_V * 2 + ICON + GAP + title + if (withSub) GAP + sub else 0.dp
    }

    /** A row height of [design] that grows when the text would not fit (large font scale). */
    @Composable
    fun minHeight(design: Dp): Dp = maxOf(design, contentHeight(withSub = true))

    /** Tight text style: exact line height and no extra font padding, so lines never clip. */
    fun tight(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        lineHeight = (size.value * LINE).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}

/**
 * The shared tile content. Put it inside a tappable, shaped container (the caller's modifier);
 * [fg] is the title colour, [iconTint] and [subColor] the icon and sub-line colours.
 */
@Composable
fun AlignedTileContent(
    icon: ImageVector,
    title: String,
    sub: String?,
    fg: Color,
    iconTint: Color,
    subColor: Color,
    modifier: Modifier = Modifier,
) {
    val needSub = TileMetrics.contentHeight(withSub = true)
    BoxWithConstraints(modifier) {
        val showSub = !sub.isNullOrBlank() && maxHeight >= needSub
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = TileMetrics.PAD_H, vertical = TileMetrics.PAD_V),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Box(Modifier.size(TileMetrics.ICON), contentAlignment = Alignment.CenterStart) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(TileMetrics.ICON))
            }
            Spacer(Modifier.height(TileMetrics.GAP))
            Text(
                title,
                color = fg,
                style = LocalTextStyle.current.merge(TileMetrics.tight(TileMetrics.titleSize())),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showSub) {
                Spacer(Modifier.height(TileMetrics.GAP))
                Text(
                    sub.orEmpty(),
                    color = subColor,
                    style = LocalTextStyle.current.merge(TileMetrics.tight(TileMetrics.subSize())),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A wide key's content (Screen off): icon and label side by side, centred both ways, the
 * optional second line under the label dropped when it cannot fit.
 */
@Composable
fun CenteredKeyContent(
    icon: ImageVector,
    title: String,
    sub: String?,
    fg: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val titleSize = LocalDims.current.t(3.25f, min = 18f)
    val subSize = LocalDims.current.t(2f, min = 13f)
    val needSub = with(density) { ((titleSize.value + subSize.value) * TileMetrics.LINE).sp.toDp() } + TileMetrics.PAD_V * 2
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val showSub = !sub.isNullOrBlank() && maxHeight >= needSub
        Row(
            Modifier.padding(horizontal = TileMetrics.PAD_H),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(TileMetrics.ICON))
            Column(verticalArrangement = Arrangement.Center) {
                Text(title, color = fg, style = LocalTextStyle.current.merge(TileMetrics.tight(titleSize)), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showSub) {
                    Text(sub.orEmpty(), color = fg.copy(alpha = 0.75f), style = LocalTextStyle.current.merge(TileMetrics.tight(subSize)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
