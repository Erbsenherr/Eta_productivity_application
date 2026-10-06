package com.example.eta.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.example.eta.ui.theme.EtaTheme

/**
 * Text, on Foundation's [BasicText].
 *
 * Colour is folded into the style here because BasicText has no colour parameter
 * of its own, and every call site would otherwise repeat the same merge.
 */
@Composable
fun EtaText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = EtaTheme.typography.body,
    color: Color = EtaTheme.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    textDecoration: TextDecoration? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(
            TextStyle(
                color = color,
                textAlign = textAlign ?: TextAlign.Unspecified,
                textDecoration = textDecoration,
            ),
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}
