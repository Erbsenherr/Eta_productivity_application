package com.example.eta.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.movedTo
import com.example.eta.domain.setup.rhythmDiverges
import com.example.eta.domain.setup.shifted
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTimePicker
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** Said wherever nights are answered and lie more than an hour apart. */
const val RHYTHM_ADVICE =
    "Deine Schlafzeiten liegen mehr als eine Stunde auseinander. Für den Schlafrhythmus " +
        "ist das nicht empfohlen. Wenn es sich nicht vermeiden lässt, achte auf " +
        "Schlafausgleich und plane gegebenenfalls einen Mittagsschlaf ein."

/**
 * The window a long press on the shaded night opens: when to go to bed, when
 * to get up, and what that makes of the night.
 *
 * One window for two different acts, and [scope] is the sentence that says
 * which: from the day planner it changes this night alone, from the week
 * timetable every night that ends on this weekday. The caller decides and
 * writes; this only asks.
 *
 * It asks for two hours. Winding down keeps its length and follows the bedtime
 * (`movedTo`), so "Bettfertig machen" is not a third question here.
 *
 * [compareWith] are the nights this one is measured against for the advice
 * about the rhythm: the standing night of the same weekday for a one-off, the
 * other six for a standing change.
 */
@Composable
fun NightEditDialog(
    title: String,
    scope: String,
    /** The night as it stands, a one-off change included. */
    night: NightTimes,
    /** The weekday the night ends on, for saying which day each hour falls on. */
    wakeDay: DayOfWeek,
    compareWith: Collection<NightTimes>,
    onDismiss: () -> Unit,
    onSave: (sleep: LocalTime, wake: LocalTime) -> Unit,
    /** Non-null while the night differs from the standing one: the way back. */
    onReset: (() -> Unit)? = null,
) {
    var sleep by remember(night) { mutableStateOf(night.sleep) }
    var wake by remember(night) { mutableStateOf(night.wake) }
    val candidate = night.movedTo(sleep, wake)

    EtaDialog(title = title, onDismiss = onDismiss) {
        EtaText(
            text = scope,
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.accent,
        )

        // The day is named beside each hour: one o'clock at night does not say
        // by itself which day's it is.
        val sleepDay = wakeDay.shifted(Math.floorDiv(candidate.sleepOffset(), MINUTES_PER_DAY))
        EtaField(label = "Schlafen gehen (${sleepDay.formatLong()})") {
            EtaTimePicker(value = sleep, onValueChange = { sleep = it })
        }
        EtaField(label = "Aufstehen (${wakeDay.formatLong()})") {
            EtaTimePicker(value = wake, onValueChange = { wake = it })
        }

        Column {
            EtaText(
                text = "Du schläfst dann ${candidate.sleepDuration().formatShort()}.",
                style = EtaTheme.typography.bodyStrong,
            )
            if (candidate.sleepDuration() != night.sleepDuration()) {
                EtaText(
                    text = "Bisher: ${night.sleepDuration().formatShort()}.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
        }

        if (rhythmDiverges(compareWith + candidate)) {
            EtaSurface(
                modifier = Modifier.fillMaxWidth(),
                borderColor = EtaTheme.colors.warning,
                contentPadding = EtaTheme.spacing.md,
            ) {
                EtaText(
                    text = RHYTHM_ADVICE,
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.warning,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                EtaButton(text = "Sichern", onClick = { onSave(sleep, wake) })
            }
            // On a line of its own: three buttons in a row do not fit a phone.
            if (onReset != null) {
                EtaButton(
                    text = "Zurück zu den üblichen Zeiten",
                    style = EtaButtonStyle.Secondary,
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
