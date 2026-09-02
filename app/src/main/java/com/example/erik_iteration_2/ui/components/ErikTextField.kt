package com.example.erik_iteration_2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.ui.theme.ErikTheme

/**
 * Single-line text input on Foundation's [BasicTextField], with the frame and
 * placeholder drawn by hand since there is no Material decoration to inherit.
 */
@Composable
fun ErikTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    /**
     * Defaults to Done on one line and to a plain return on several: a
     * multi-line field whose Enter key submits cannot be given a paragraph, and
     * these fields are notes, contract terms and diary entries.
     */
    imeAction: ImeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
    onImeAction: () -> Unit = {},
) {
    val shape = ErikTheme.shapes.small
    val textStyle: TextStyle = ErikTheme.typography.body
        .merge(TextStyle(color = ErikTheme.colors.textPrimary))

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(ErikTheme.colors.surface, shape)
            .border(1.dp, ErikTheme.colors.border, shape)
            // Room for a few lines before it starts growing, so a note field does
            // not look like a one-line box that happens to accept more.
            .then(if (singleLine) Modifier else Modifier.heightIn(min = 84.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        textStyle = textStyle,
        singleLine = singleLine,
        cursorBrush = SolidColor(ErikTheme.colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions { onImeAction() },
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty() && placeholder != null) {
                    ErikText(
                        text = placeholder,
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.textMuted,
                    )
                }
                innerTextField()
            }
        },
    )
}
