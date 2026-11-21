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
import com.example.eta.domain.planning.SleepPrompts
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTimePickerDialog
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatPoints
import com.example.eta.domain.model.PUNCTUAL_WAKE_POINTS
import com.example.eta.domain.model.SLEEP_LATE_PENALTY
import com.example.eta.domain.model.SLEEP_ZERO_MINUTES
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
    onBed: (day: LocalDate, at: LocalTime?, punctual: Boolean) -> Unit,
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
                        text = gradingText(prompts.tolerance, "Aufstehzeit"),
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            } else {
                EtaText(
                    text = "Aufgestanden um ${answer.wokeAt?.formatClock().orEmpty()} — " +
                        (if (answer.wokeOnTime == true) "pünktlich." else "nicht pünktlich.") +
                        if (pointsVisible) " ${pointsSentence(answer.wakePoints)}" else "",
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
                val points = prompts.eveningAnswer?.bedPoints ?: 0.0
                EtaText(
                    text = "Schlaf gut!" +
                        if (pointsVisible) " ${pointsSentence(points)} Mit der nächsten Abrechnung." else "",
                    style = EtaTheme.typography.body,
                    color = if (points < 0.0) EtaTheme.colors.textSecondary else EtaTheme.colors.success,
                )
            } else {
                EtaButton(
                    text = "Gehe nun schlafen",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onBed(evening, null, false) },
                )
                if (pointsVisible) {
                    EtaText(
                        text = gradingText(prompts.tolerance, "Schlafenszeit"),
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
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
    onBed: (day: LocalDate, at: LocalTime?, punctual: Boolean) -> Unit,
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
                        text = "»Pünktlich« heißt: Du lagst spätestens ${prompts.tolerance} " +
                            "Minuten nach deiner Schlafenszeit im Bett und hast zu schlafen versucht. " +
                            gradingText(prompts.tolerance, "Schlafenszeit"),
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
                EtaText(
                    text = "Bist du gestern pünktlich schlafen gegangen?",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
                var pickingBed by remember { mutableStateOf(false) }
                EtaButton(
                    text = "Pünktlich",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onBed(bedDay, null, true) },
                )
                EtaButton(
                    text = "Bin um … Uhr schlafen gegangen",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pickingBed = true },
                )
                if (pickingBed) {
                    EtaTimePickerDialog(
                        initial = prompts.bedDue ?: LocalTime(23, 0),
                        onDismiss = { pickingBed = false },
                        onConfirm = {
                            onBed(bedDay, it, false)
                            pickingBed = false
                        },
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

/**
 * The grading in one sentence (step 59), for the hour of getting up or the
 * bedtime: full points within the tolerance, five minutes of nothing, then
 * a deduction per quarter begun, up to two hours.
 */
private fun gradingText(tolerance: Int, what: String): String =
    "Bis $tolerance Minuten nach der $what gibt es ${formatPoints(PUNCTUAL_WAKE_POINTS)} Punkte, " +
        "die nächsten $SLEEP_ZERO_MINUTES Minuten 0, danach −${formatPoints(SLEEP_LATE_PENALTY)} " +
        "je angefangene Viertelstunde, höchstens bis zwei Stunden danach."

/** "Das gibt +0,5 Punkte." / "Das kostet 1 Punkt." */
private fun pointsSentence(points: Double): String = when {
    points > 0.0 -> "Das gibt +${formatPoints(points)} Punkte."
    points < 0.0 -> "Das kostet ${formatPoints(-points)} Punkte."
    else -> "Das gibt keine Punkte."
}
