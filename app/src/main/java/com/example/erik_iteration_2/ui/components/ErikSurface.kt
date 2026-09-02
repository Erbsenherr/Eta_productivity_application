package com.example.erik_iteration_2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.ui.theme.ErikTheme

/**
 * A panel: background, optional hairline border, rounded corners.
 *
 * Separation comes from the border rather than an elevation shadow — shadows read
 * poorly on the dark palette and would blur the day planner's dense stacking.
 */
@Composable
fun ErikSurface(
    modifier: Modifier = Modifier,
    color: Color = ErikTheme.colors.surface,
    borderColor: Color? = ErikTheme.colors.border,
    shape: Shape = ErikTheme.shapes.medium,
    contentPadding: Dp = ErikTheme.spacing.lg,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(color = color, shape = shape)
            .then(
                if (borderColor != null) {
                    Modifier.border(width = 1.dp, color = borderColor, shape = shape)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}
