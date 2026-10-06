package com.example.eta.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.eta.ui.theme.EtaTheme

/**
 * The plain dialog frame every screen had its own private copy of.
 *
 * Not a replacement for those where the frame carries something of its own — the
 * planner's is 380dp wide and scrolls a long form — but the shape is the same, and
 * a fifth hand-rolled `Dialog { EtaSurface { Column { … } } }` was one too many.
 */
@Composable
fun EtaDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        EtaSurface(
            modifier = modifier.widthIn(max = 380.dp),
            color = EtaTheme.colors.surfaceRaised,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
            ) {
                EtaText(text = title, style = EtaTheme.typography.title)
                content()
            }
        }
    }
}

/** A sentence and two buttons — the smallest dialog there is. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    dismiss: String = "Abbrechen",
) {
    EtaDialog(title = title, onDismiss = onDismiss) {
        EtaText(
            text = message,
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = dismiss, style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(text = confirm, onClick = onConfirm)
        }
    }
}
