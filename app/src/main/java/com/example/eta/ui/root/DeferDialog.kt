package com.example.eta.ui.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.eta.domain.planning.MAX_EMERGENCY_HOURS
import com.example.eta.domain.planning.PlanningPhase
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaStepper
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.theme.EtaTheme

/**
 * The "NOTFALL": pushing a planning phase back by hand.
 *
 * `Planungsphase.md` calls it a manual entry of hours, which a notification button
 * cannot ask for — so the alarm's third action opens this instead of guessing.
 */
@Composable
fun DeferDialog(
    phase: PlanningPhase,
    onDismiss: () -> Unit,
    onDefer: (hours: Int) -> Unit,
) {
    var hours by remember { mutableIntStateOf(2) }

    Dialog(onDismissRequest = onDismiss) {
        EtaSurface(
            modifier = Modifier.widthIn(max = 360.dp),
            color = EtaTheme.colors.surfaceRaised,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
                EtaText(text = "NOTFALL", style = EtaTheme.typography.title)
                EtaText(
                    text = when (phase) {
                        PlanningPhase.DAILY -> "Die Tagesplanung wird verschoben."
                        PlanningPhase.WEEKLY -> "Die Wochenplanung wird verschoben."
                    },
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )

                EtaField(
                    label = "Um wie viele Stunden?",
                    hint = "Höchstens $MAX_EMERGENCY_HOURS — weiter zu schieben hieße, " +
                        "die Phase ausfallen zu lassen.",
                ) {
                    EtaStepper(
                        value = if (hours == 1) "1 Stunde" else "$hours Stunden",
                        valueWidth = 96.dp,
                        onDecrement = { hours = (hours - 1).coerceAtLeast(1) },
                        onIncrement = { hours = (hours + 1).coerceAtMost(MAX_EMERGENCY_HOURS) },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Abbrechen",
                        style = EtaButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    EtaButton(text = "Verschieben", onClick = { onDefer(hours) })
                }
            }
        }
    }
}
