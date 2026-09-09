package com.example.erik_iteration_2.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikStepperButton
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.format.formatPoints
import com.example.erik_iteration_2.ui.theme.ErikTheme

/** The scale the whole app counts in — half points, and never anything finer. */
private const val STEP = 0.5

/**
 * Booking points by hand, from a long press on the account.
 *
 * It writes a row rather than setting a number, because the balance *is* the
 * ledger: a correction that overwrote the total would leave a figure whose
 * provenance nobody could reconstruct a month later. The note is optional and is
 * the only thing that will say, later, what this was for.
 */
@Composable
fun ManualPointsDialog(
    balance: Double,
    onDismiss: () -> Unit,
    onBook: (amount: Double, note: String?) -> Unit,
) {
    var amount by remember { mutableDoubleStateOf(STEP) }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        ErikSurface(
            modifier = Modifier.widthIn(max = 380.dp),
            color = ErikTheme.colors.surfaceRaised,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
                ErikText(text = "Punkte buchen", style = ErikTheme.typography.title)
                ErikText(
                    text = "Aktuell ${formatPoints(balance)} Punkte. Danach " +
                        "${formatPoints(balance + amount)}.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textSecondary,
                )

                ErikField(label = "Betrag", hint = "Plus schreibt gut, minus zieht ab.") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ErikStepperButton(label = "−", onClick = { amount -= STEP })
                        ErikText(
                            text = if (amount > 0) "+${formatPoints(amount)}" else formatPoints(amount),
                            style = ErikTheme.typography.display,
                            color = when {
                                amount > 0 -> ErikTheme.colors.success
                                amount < 0 -> ErikTheme.colors.danger
                                else -> ErikTheme.colors.textMuted
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ErikStepperButton(label = "+", onClick = { amount += STEP })
                    }
                }

                ErikField(label = "Notiz") {
                    ErikTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = "Wofür?",
                        singleLine = false,
                    )
                }

                ErikText(
                    text = "Wird als eigene Zeile im Punktekonto geführt — der Verlauf " +
                        "bleibt nachvollziehbar.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                    ErikButton(
                        text = "Abbrechen",
                        style = ErikButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    ErikButton(
                        text = "Buchen",
                        enabled = amount != 0.0,
                        onClick = { onBook(amount, note.trim().ifBlank { null }) },
                    )
                }
            }
        }
    }
}
