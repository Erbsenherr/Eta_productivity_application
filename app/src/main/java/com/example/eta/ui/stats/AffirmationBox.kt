package com.example.eta.ui.stats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.eta.domain.stats.Mood
import com.example.eta.domain.stats.affirmation
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.theme.EtaTheme

/**
 * A short word of encouragement or praise (step 59), under the Wochenrückblick
 * and in the Tagesabschluss: worse than usual encourages, as good or better
 * praises. [seed] keeps the message steady while the page is open.
 */
@Composable
fun AffirmationBox(mood: Mood, seed: Int, modifier: Modifier = Modifier) {
    val tint = when (mood) {
        Mood.WORSE -> EtaTheme.colors.accent
        Mood.USUAL, Mood.BETTER -> EtaTheme.colors.success
    }
    EtaSurface(modifier = modifier.fillMaxWidth(), borderColor = tint) {
        EtaText(
            text = affirmation(mood, seed),
            style = EtaTheme.typography.bodyStrong,
            color = tint,
        )
    }
}
