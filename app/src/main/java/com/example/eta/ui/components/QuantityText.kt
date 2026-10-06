package com.example.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.eta.domain.growth.quantityLabel
import com.example.eta.domain.model.Item
import com.example.eta.ui.theme.EtaTheme

/**
 * The Mengen-Inkrement's count, where a note would stand — but louder.
 *
 * A note is background and is drawn muted; the count is the one number the task
 * is done *with* today, so it is set in the strong body style and the accent
 * colour. One composable, so every place that shows it shows it the same way.
 * Draws nothing for an item without a count.
 */
@Composable
fun QuantityText(item: Item, modifier: Modifier = Modifier) {
    val label = item.quantityLabel() ?: return
    EtaText(
        text = label,
        style = EtaTheme.typography.bodyStrong,
        color = EtaTheme.colors.accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
