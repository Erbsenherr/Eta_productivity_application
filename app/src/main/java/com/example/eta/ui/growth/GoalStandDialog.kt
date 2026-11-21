package com.example.eta.ui.growth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.eta.domain.growth.MIN_GROWTH_DURATION
import com.example.eta.domain.growth.MIN_QUANTITY
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaCountPicker
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The most a count can be put to by hand where it has no target. */
private const val MAX_HAND_COUNT = 9999

/**
 * "Stand setzen" (step 59): where a goal stands, put by hand — for a task
 * done outside the app, whose sittings the app never saw.
 *
 * [time] / [target] for a "Zielzeit", [count] / [countTarget] for a
 * Mengen-Inkrement; whichever is null is not offered. Nothing is billed and no
 * completion is counted; the completions towards the next step start again.
 */
@Composable
fun GoalStandDialog(
    name: String,
    time: Duration?,
    target: Duration?,
    count: Int?,
    countTarget: Int?,
    onDismiss: () -> Unit,
    onConfirm: (time: Duration?, count: Int?) -> Unit,
) {
    var newTime by remember { mutableStateOf(time) }
    var newCount by remember { mutableIntStateOf(count ?: MIN_QUANTITY) }

    EtaDialog(title = "Stand setzen", onDismiss = onDismiss) {
        EtaText(
            text = "»$name« außerhalb der App gemacht? Trag hier ein, wo die Aufgabe jetzt " +
                "steht. Das gilt für alle ihre Wochentage; Punkte gibt es dafür keine, und die " +
                "Abschlüsse bis zum nächsten Schritt zählen von vorn.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )
        val current = newTime
        if (current != null && target != null) {
            EtaField(label = "Zeit", hint = "Ziel: ${target.formatShort()}") {
                EtaDurationPicker(
                    value = current,
                    onValueChange = { newTime = it.coerceIn(MIN_GROWTH_DURATION, maxOf(MIN_GROWTH_DURATION, target)) },
                    step = 1.minutes,
                    minimum = MIN_GROWTH_DURATION,
                    precise = true,
                )
            }
        }
        if (count != null) {
            EtaField(
                label = "Anzahl",
                hint = countTarget?.let { "Ziel: $it" },
            ) {
                EtaCountPicker(
                    value = newCount,
                    onValueChange = { newCount = it },
                    range = MIN_QUANTITY..(countTarget ?: MAX_HAND_COUNT),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            EtaButton(
                text = "Übernehmen",
                onClick = {
                    onConfirm(
                        newTime?.takeIf { it != time },
                        newCount.takeIf { count != null && it != count },
                    )
                },
            )
        }
    }
}
