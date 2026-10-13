package com.example.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.eta.ui.theme.EtaTheme

/**
 * One colour to choose: a filled circle, ringed while it is the chosen one.
 *
 * Shared by the two places a colour is picked from a row of them — the accent in
 * the Design card and a task's own colour in the Extras box — so a swatch looks
 * and is hit the same in both.
 */
@Composable
fun EtaSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(40.dp)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) EtaTheme.colors.textPrimary else EtaTheme.colors.border,
                shape = CircleShape,
            )
            .padding(5.dp)
            .background(color, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    )
}
