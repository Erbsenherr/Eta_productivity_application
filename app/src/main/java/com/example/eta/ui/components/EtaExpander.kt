package com.example.eta.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.eta.ui.theme.EtaTheme

/**
 * A section of a form that can be folded away.
 *
 * The same shape the Listen tab's cards already use — heading, a count on the
 * right, a ▾/▴ — so a fold reads the same wherever it appears. It keeps its own
 * state and keeps it across a rotation: what the user folded away should stay
 * folded away.
 *
 * [summary] is what the closed header says is inside. A fold that gives no
 * account of what it hides makes the user open it to find out, which costs more
 * than leaving it open would have.
 */
@Composable
fun EtaExpander(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(label) { mutableStateOf(initiallyExpanded) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { expanded = !expanded },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                EtaText(text = label, style = EtaTheme.typography.label)
                val caption = if (expanded) hint else summary ?: hint
                caption?.let {
                    EtaText(
                        text = it,
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            }
            Spacer(Modifier.size(EtaTheme.spacing.sm))
            EtaText(
                text = if (expanded) "▴" else "▾",
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.textMuted,
            )
        }

        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                content()
            }
        }
    }
}
