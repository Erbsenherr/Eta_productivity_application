package com.example.erik_iteration_2.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.ui.theme.ErikTheme

/** One destination in the bar. */
data class ErikTabItem(
    val id: String,
    val label: String,
)

/**
 * The bottom tab bar, drawn by hand like everything else here.
 *
 * Labels rather than icons: the four destinations are "Heute", "Listen",
 * "Verträge", "Einstellungen" — words this app can say in its own voice, where
 * icons for a Sperrliste or a self-contract would have to be invented and then
 * explained. The active one is marked by a short accent bar above it and by
 * weight, so the state survives being read in a hurry.
 */
@Composable
fun ErikTabBar(
    items: List<ErikTabItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ErikTheme.colors.border),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ErikTheme.colors.surface)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                ErikTab(
                    item = item,
                    selected = item.id == selectedId,
                    onClick = { onSelect(item.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ErikTab(
    item: ErikTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val emphasis by animateFloatAsState(if (selected) 1f else 0.55f, label = "tabEmphasis")

    Column(
        modifier = modifier
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = ErikTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(3.dp)
                .background(
                    color = if (selected) ErikTheme.colors.accent else ErikTheme.colors.surface,
                    shape = ErikTheme.shapes.pill,
                ),
        )
        Box(Modifier.height(ErikTheme.spacing.xs))
        ErikText(
            text = item.label,
            style = if (selected) ErikTheme.typography.label else ErikTheme.typography.caption,
            color = if (selected) ErikTheme.colors.accent else ErikTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(emphasis),
        )
    }
}
