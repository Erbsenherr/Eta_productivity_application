package com.example.eta.ui.streaks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.flameCore
import com.example.eta.ui.components.flameOutline
import com.example.eta.ui.theme.EtaTheme

/** The flame's own colours: a fire is orange in every design and by night as well. */
private val FlameOuter = Color(0xFFF97316)
private val FlameInner = Color(0xFFFACC15)

/**
 * A streak's flame and its number (step 48).
 *
 * [lit] false is a flame gone out — or one not yet lit, for a streak waiting
 * for its first completion: the outline in the muted colour, no fire in it.
 */
@Composable
fun StreakFlame(
    count: Int,
    lit: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val muted = EtaTheme.colors.textMuted
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(size)) {
            val unit = this.size.minDimension / 24f
            if (lit) {
                drawPath(flameOutline(unit), FlameOuter)
                drawPath(flameCore(unit), FlameInner)
            } else {
                drawPath(flameOutline(unit), muted, style = Stroke(width = 1.8f * unit))
            }
        }
        Spacer(Modifier.size(EtaTheme.spacing.xs))
        EtaText(
            text = count.toString(),
            style = EtaTheme.typography.title,
            color = if (lit) FlameOuter else muted,
        )
    }
}
