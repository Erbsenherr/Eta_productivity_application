package com.example.eta.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.eta.ui.theme.EtaTheme

/** One destination in the bar. */
data class EtaTabItem(
    val id: String,
    val label: String,
)

/**
 * The bottom tab bar, drawn by hand like everything else here.
 *
 * Labels rather than icons: the destinations are "Heute", "Listen", "Verträge",
 * "Erinnerungen", "Einstellungen" and "Growth-Tasks" — words this app can say in
 * its own voice, where icons for a Sperrliste or a self-contract would have to be
 * invented and then explained. The active one is marked by a short accent bar
 * above it and by weight, so the state survives being read in a hurry.
 *
 * **Every label is drawn whole, and the bar scrolls instead.** Six of them
 * sharing one phone width meant "Erinnerungen" and "Einstellungen" were cut off,
 * and a tab whose name is cut off is a tab the user has to guess at. So each tab
 * is as wide as its own word and the row scrolls sideways — which is also the
 * only shape that survives a seventh destination.
 *
 * The selected tab is brought into view, so arriving on one that is off-screen —
 * from a back press, or from a restored state — still shows where you are.
 */
@Composable
fun EtaTabBar(
    items: List<EtaTabItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    // Measured rather than estimated: the labels are different lengths, so the
    // only honest way to bring one into view is to know where it landed.
    val positions = remember { mutableStateMapOf<String, IntRange>() }
    var viewportWidth by remember { mutableStateOf(0) }

    LaunchedEffect(selectedId, positions[selectedId], viewportWidth) {
        val bounds = positions[selectedId] ?: return@LaunchedEffect
        if (viewportWidth == 0) return@LaunchedEffect
        val offset = scrollState.value
        when {
            bounds.first < offset -> scrollState.animateScrollTo(bounds.first)
            bounds.last > offset + viewportWidth ->
                scrollState.animateScrollTo(bounds.last - viewportWidth)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(EtaTheme.colors.border),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EtaTheme.colors.surface)
                .navigationBarsPadding()
                // Before the scroll modifier, so this is the viewport rather
                // than the scrolling content.
                .onGloballyPositioned { viewportWidth = it.size.width }
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.Start,
        ) {
            items.forEach { item ->
                EtaTab(
                    item = item,
                    selected = item.id == selectedId,
                    onClick = { onSelect(item.id) },
                    onBounds = { positions[item.id] = it },
                )
            }
        }
    }
}

@Composable
private fun EtaTab(
    item: EtaTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    onBounds: (IntRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val emphasis by animateFloatAsState(if (selected) 1f else 0.55f, label = "tabEmphasis")

    Column(
        modifier = modifier
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            // Wide enough that a short word is still a comfortable target, and
            // free to grow past that rather than clipping a long one.
            .widthIn(min = 72.dp)
            .padding(vertical = EtaTheme.spacing.sm, horizontal = EtaTheme.spacing.md)
            .onGloballyPositioned { coordinates ->
                val left = coordinates.positionInParent().x.toInt()
                onBounds(left..(left + coordinates.size.width))
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(3.dp)
                .background(
                    color = if (selected) EtaTheme.colors.accent else EtaTheme.colors.surface,
                    shape = EtaTheme.shapes.pill,
                ),
        )
        Box(Modifier.height(EtaTheme.spacing.xs))
        EtaText(
            text = item.label,
            style = if (selected) EtaTheme.typography.label else EtaTheme.typography.caption,
            color = if (selected) EtaTheme.colors.accent else EtaTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            // One line, and never clipped: inside a horizontal scroll the width
            // is unbounded, so a long label is measured whole rather than cut.
            maxLines = 1,
            modifier = Modifier.alpha(emphasis),
        )
    }
}
