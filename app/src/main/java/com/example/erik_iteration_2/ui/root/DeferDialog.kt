package com.example.erik_iteration_2.ui.root

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
import com.example.erik_iteration_2.domain.planning.MAX_EMERGENCY_HOURS
import com.example.erik_iteration_2.domain.planning.PlanningPhase
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikStepper
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.theme.ErikTheme

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
        ErikSurface(
            modifier = Modifier.widthIn(max = 360.dp),
            color = ErikTheme.colors.surfaceRaised,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
                ErikText(text = "NOTFALL", style = ErikTheme.typography.title)
                ErikText(
                    text = when (phase) {
                        PlanningPhase.DAILY -> "Die Tagesplanung wird verschoben."
                        PlanningPhase.WEEKLY -> "Die Wochenplanung wird verschoben."
                    },
                    style = ErikTheme.typography.body,
                    color = ErikTheme.colors.textSecondary,
                )

                ErikField(
                    label = "Um wie viele Stunden?",
                    hint = "Höchstens $MAX_EMERGENCY_HOURS — weiter zu schieben hieße, " +
                        "die Phase ausfallen zu lassen.",
                ) {
                    ErikStepper(
                        value = if (hours == 1) "1 Stunde" else "$hours Stunden",
                        valueWidth = 96.dp,
                        onDecrement = { hours = (hours - 1).coerceAtLeast(1) },
                        onIncrement = { hours = (hours + 1).coerceAtMost(MAX_EMERGENCY_HOURS) },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                    ErikButton(
                        text = "Abbrechen",
                        style = ErikButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    ErikButton(text = "Verschieben", onClick = { onDefer(hours) })
                }
            }
        }
    }
}
