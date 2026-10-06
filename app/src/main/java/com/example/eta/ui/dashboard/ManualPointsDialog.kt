package com.example.eta.ui.dashboard

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
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaStepperButton
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.theme.EtaTheme

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
        EtaSurface(
            modifier = Modifier.widthIn(max = 380.dp),
            color = EtaTheme.colors.surfaceRaised,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
                EtaText(text = "Punkte buchen", style = EtaTheme.typography.title)
                EtaText(
                    text = "Aktuell ${formatPoints(balance)} Punkte. Danach " +
                        "${formatPoints(balance + amount)}.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )

                EtaField(label = "Betrag", hint = "Plus schreibt gut, minus zieht ab.") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EtaStepperButton(label = "−", onClick = { amount -= STEP })
                        EtaText(
                            text = if (amount > 0) "+${formatPoints(amount)}" else formatPoints(amount),
                            style = EtaTheme.typography.display,
                            color = when {
                                amount > 0 -> EtaTheme.colors.success
                                amount < 0 -> EtaTheme.colors.danger
                                else -> EtaTheme.colors.textMuted
                            },
                            modifier = Modifier.weight(1f),
                        )
                        EtaStepperButton(label = "+", onClick = { amount += STEP })
                    }
                }

                EtaField(label = "Notiz") {
                    EtaTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = "Wofür?",
                        singleLine = false,
                    )
                }

                EtaText(
                    text = "Wird als eigene Zeile im Punktekonto geführt — der Verlauf " +
                        "bleibt nachvollziehbar.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Abbrechen",
                        style = EtaButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    EtaButton(
                        text = "Buchen",
                        enabled = amount != 0.0,
                        onClick = { onBook(amount, note.trim().ifBlank { null }) },
                    )
                }
            }
        }
    }
}
