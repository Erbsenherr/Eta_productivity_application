package com.example.erik_iteration_2.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.erik_iteration_2.ui.theme.ErikTheme

/**
 * Text, on Foundation's [BasicText].
 *
 * Colour is folded into the style here because BasicText has no colour parameter
 * of its own, and every call site would otherwise repeat the same merge.
 */
@Composable
fun ErikText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ErikTheme.typography.body,
    color: Color = ErikTheme.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = color, textAlign = textAlign ?: TextAlign.Unspecified)),
        maxLines = maxLines,
        overflow = overflow,
    )
}
