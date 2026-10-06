package com.example.eta.ui.components

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.eta.ui.theme.EtaTheme

/**
 * Single-line text input on Foundation's [BasicTextField], with the frame and
 * placeholder drawn by hand since there is no Material decoration to inherit.
 */
@Composable
fun EtaTextField(
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
    /** [KeyboardType.Number] where only a number is an answer. */
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val shape = EtaTheme.shapes.small
    val textStyle: TextStyle = EtaTheme.typography.body
        .merge(TextStyle(color = EtaTheme.colors.textPrimary))

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(EtaTheme.colors.surface, shape)
            .border(1.dp, EtaTheme.colors.border, shape)
            // Room for a few lines before it starts growing, so a note field does
            // not look like a one-line box that happens to accept more.
            .then(if (singleLine) Modifier else Modifier.heightIn(min = 84.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        textStyle = textStyle,
        singleLine = singleLine,
        cursorBrush = SolidColor(EtaTheme.colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions { onImeAction() },
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty() && placeholder != null) {
                    EtaText(
                        text = placeholder,
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textMuted,
                    )
                }
                innerTextField()
            }
        },
    )
}
