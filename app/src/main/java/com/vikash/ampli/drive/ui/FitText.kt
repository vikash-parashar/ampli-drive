package com.vikash.ampli.drive.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** How a [FitText] label shrinks: 8% a step, never below [FitTextRule.MIN_SP]. Pure. */
internal object FitTextRule {
    const val STEP = 0.92f
    const val MIN_SP = 9f

    fun next(currentSp: Float): Float = maxOf(MIN_SP, currentSp * STEP)
}

/**
 * A one-line button label that is never cut ("CONTRO…"): it starts at [fontSize] and shrinks
 * until the whole word fits the key, centred. Drawn only once it fits, so no frame shows the
 * clipped size. The shrink happens on the first layout only (a few passes, once per label).
 */
@Composable
fun FitText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = FontWeight.Bold,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = 0.04.em,
) {
    var size by remember(text, fontSize) { mutableFloatStateOf(fontSize.value) }
    var ready by remember(text, fontSize) { mutableFloatStateOf(0f) }
    Text(
        text,
        color = color,
        fontSize = size.sp,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        onTextLayout = { r ->
            if (r.hasVisualOverflow && size > FitTextRule.MIN_SP) {
                size = FitTextRule.next(size)
            } else {
                ready = 1f
            }
        },
        modifier = modifier.drawWithContent { if (ready > 0f) drawContent() },
    )
}
