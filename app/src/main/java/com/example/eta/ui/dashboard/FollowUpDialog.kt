package com.example.eta.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme

/**
 * What a tick leaves to decide: how a task finished early is billed, and what to
 * do with the time it just handed back.
 *
 * Either half can be absent. The last task of the day has no next one to pull
 * forward but can still be finished early, and a task that ran its course has
 * nothing to bill differently.
 *
 * For the next task there are three answers, and the middle one is the ordinary
 * case: the next thing is ready, so start it now rather than sitting out the gap.
 * The third exists because the gap is often exactly what one has earned — a
 * quarter of an hour, then on.
 *
 * Keeping the plan is the safe answer and therefore the one that also closes the
 * dialog when it is dismissed: nothing in the day changes unless it is asked for.
 * The billing choice is written the moment it is made, so no way out loses it.
 */
@Composable
fun FollowUpDialog(
    question: FollowUpQuestion,
    onBilledInFull: (Boolean) -> Unit,
    onKeepPlan: () -> Unit,
    onPullForward: () -> Unit,
    onBreakThenPull: () -> Unit,
) {
    val billing = question.billing
    val next = question.next

    EtaDialog(
        title = if (billing != null) "Früher fertig" else "Und weiter?",
        onDismiss = onKeepPlan,
    ) {
        if (billing != null) {
            BillingSection(billing, onBilledInFull)
        }

        if (next != null) {
            EtaText(
                text = "Als Nächstes steht »${next.name}« um " +
                    "${next.plannedAt.formatClock()} an.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(
                    text = "Task vorziehen",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPullForward,
                )
                EtaButton(
                    text = "Pause einfügen, dann vorziehen",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBreakThenPull,
                )
                EtaButton(
                    text = "Plan beibehalten",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onKeepPlan,
                )
            }
            EtaText(
                text = "Vorgezogen wird nur diese eine Aufgabe, und nur so weit, wie sie passt — " +
                    "der Rest des Tages bleibt, wo er ist.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        } else {
            EtaButton(
                text = "Fertig",
                modifier = Modifier.fillMaxWidth(),
                onClick = onKeepPlan,
            )
        }
    }
}

/**
 * "Dennoch voll abrechnen?" — asked because finishing early is not to be punished.
 *
 * Both lengths are spelled out, since the answer is a number and "voll" is not
 * always the plan's: at most twice the time used is billed, and the options say
 * what that comes to rather than leaving it to be worked out.
 */
@Composable
private fun BillingSection(choice: BillingChoice, onBilledInFull: (Boolean) -> Unit) {
    val billing = choice.billing

    EtaText(
        text = "»${choice.name}« war auf ${billing.planned.formatShort()} angesetzt, " +
            "du warst nach ${billing.used.formatShort()} fertig. Soll die Aufgabe " +
            "dennoch voll abgerechnet werden?",
        style = EtaTheme.typography.body,
        color = EtaTheme.colors.textSecondary,
    )
    EtaChoice(
        options = listOf(
            true to "Voll abrechnen — ${billing.full.formatShort()}",
            false to "Nur die gebrauchte Zeit — ${billing.used.formatShort()}",
        ),
        selected = choice.full,
        onSelect = onBilledInFull,
    )
    EtaText(
        text = if (billing.isCapped) {
            "Angerechnet wird höchstens das Doppelte der gebrauchten Zeit; die übrigen " +
                "${(billing.planned - billing.full).formatShort()} verfallen. Gebucht " +
                "wird abends mit dem Tag."
        } else {
            "Gebucht wird abends mit dem Tag."
        },
        style = EtaTheme.typography.caption,
        color = EtaTheme.colors.textMuted,
    )
}
