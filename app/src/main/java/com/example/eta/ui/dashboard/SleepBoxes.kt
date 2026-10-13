package com.example.eta.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.eta.domain.model.PUNCTUAL_WAKE_MINUTES
import com.example.eta.domain.planning.SleepPrompts
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTimePickerDialog
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * What the "Gerade" box asks while it is called "Guten Morgen!" or "Gute Nacht!".
 *
 * In the morning, three ways of saying when one got up — now, on time, or at an
 * hour of one's own. In the evening, one button. Once answered it says what was
 * answered and asks nothing more; the box keeps its greeting until the window
 * closes.
 */
@Composable
fun SleepGreeting(
    prompts: SleepPrompts,
    onWake: (at: LocalTime?, due: LocalTime) -> Unit,
    onBed: (day: LocalDate, onTime: Boolean) -> Unit,
) {
    val due = prompts.wakeDue
    val pointsVisible = LocalPointsVisible.current

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        if (prompts.morning && due != null) {
            val answer = prompts.morningAnswer
            if (answer == null) {
                EtaText(
                    text = "Aufstehzeit heute: ${due.formatClock()}. Wann bist du aufgestanden?",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
                WakeButtons(due = due, offerNow = true, onWake = onWake)
                if (pointsVisible) {
                    EtaText(
                        text = "Pünktlich heißt: höchstens $PUNCTUAL_WAKE_MINUTES Minuten nach der " +
                            "Aufstehzeit. Dafür gibt es am Abend einen halben Punkt.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            } else {
                EtaText(
                    text = "Aufgestanden um ${answer.wokeAt?.formatClock().orEmpty()} — " +
                        if (answer.wokeOnTime == true) "pünktlich." else "nicht pünktlich.",
                    style = EtaTheme.typography.body,
                    color = if (answer.wokeOnTime == true) {
                        EtaTheme.colors.success
                    } else {
                        EtaTheme.colors.textSecondary
                    },
                )
            }
        }

        val evening = prompts.eveningOf
        if (evening != null) {
            if (prompts.eveningAnswered) {
                EtaText(
                    text = "Schlaf gut!" +
                        if (pointsVisible) " Der halbe Punkt kommt mit der nächsten Abrechnung." else "",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.success,
                )
            } else {
                EtaButton(
                    text = "Gehe nun schlafen",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onBed(evening, true) },
                )
            }
        }
    }
}

/**
 * The two boxes that stay when a window closed unanswered: the hour of getting
 * up, until the day ends, and "Schlafenszeit – Vortag".
 *
 * Standing boxes rather than a question that vanishes, because both feed the
 * statistics, and a day nobody answered for is simply missing from them.
 */
@Composable
fun SleepCatchUpBoxes(
    prompts: SleepPrompts,
    onWake: (at: LocalTime?, due: LocalTime) -> Unit,
    onBed: (day: LocalDate, onTime: Boolean) -> Unit,
) {
    val due = prompts.wakeDue
    if (prompts.morningOwed && due != null) {
        EtaSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                BoxHeading("Aufstehzeit – heute")
                EtaText(
                    text = "Wann bist du heute aufgestanden? Vorgesehen war ${due.formatClock()}.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
                WakeButtons(due = due, offerNow = false, onWake = onWake)
            }
        }
    }

    val bedDay = prompts.bedOwedFor
    if (bedDay != null) {
        var info by remember { mutableStateOf(false) }
        EtaSurface(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BoxHeading("Schlafenszeit – Vortag", Modifier.weight(1f))
                    EtaButton(
                        text = "i",
                        style = EtaButtonStyle.Secondary,
                        onClick = { info = !info },
                    )
                }
                if (info) {
                    EtaText(
                        text = "»Pünktlich« heißt: Du lagst innerhalb deiner Bettgehzeit im Bett " +
                            "und hast zu schlafen versucht.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
                EtaText(
                    text = "Bist du gestern pünktlich schlafen gegangen?",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Pünktlich",
                        modifier = Modifier.weight(1f),
                        onClick = { onBed(bedDay, true) },
                    )
                    EtaButton(
                        text = "Unpünktlich",
                        style = EtaButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = { onBed(bedDay, false) },
                    )
                }
            }
        }
    }
}

/** "Jetzt" (only while the window is open), "Pünktlich", or an hour of one's own. */
@Composable
private fun WakeButtons(
    due: LocalTime,
    offerNow: Boolean,
    onWake: (at: LocalTime?, due: LocalTime) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        if (offerNow) {
            EtaButton(
                text = "Bin jetzt aufgestanden",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onWake(null, due) },
            )
        }
        EtaButton(
            text = "Bin pünktlich aufgestanden",
            style = EtaButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onWake(due, due) },
        )
        EtaButton(
            text = "Bin um … Uhr aufgestanden",
            style = EtaButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            onClick = { picking = true },
        )
    }

    if (picking) {
        EtaTimePickerDialog(
            initial = due,
            onDismiss = { picking = false },
            onConfirm = {
                onWake(it, due)
                picking = false
            },
        )
    }
}
