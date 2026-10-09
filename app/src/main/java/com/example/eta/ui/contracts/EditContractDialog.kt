package com.example.eta.ui.contracts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.ContractEffort
import com.example.eta.domain.model.ContractState
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaCheckbox
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Changing a contract's wording — once, and at the price of its term.
 *
 * What may be changed is the **text**: the name, what it asks, and what counts as
 * breaking it. The effort, and with it the daily payout, deliberately may not —
 * an edit that could raise the rate would turn "bearbeiten" into a way of paying
 * yourself more for a promise already half-served.
 *
 * What it costs is the run-up. `signedOn` becomes today and the end moves with it,
 * so a contract two weeks into its term is back at zero and needs a full month
 * again before it can become legacy. That is the rule rather than a side effect:
 * changing what you promised is making a new promise.
 */
@Composable
fun EditContractDialog(
    contract: Contract,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, conditions: String, breach: String) -> Unit,
) {
    var title by remember(contract.id) { mutableStateOf(contract.title) }
    var conditions by remember(contract.id) { mutableStateOf(contract.conditions) }
    var breach by remember(contract.id) { mutableStateOf(contract.breachDefinition) }
    var accepted by remember(contract.id) { mutableStateOf(false) }

    val termDays = contract.signedOn.daysUntil(contract.endsOn)
    val newEnd = today.plus(DatePeriod(days = termDays))
    val servedDays = contract.signedOn.daysUntil(today).coerceAtLeast(0)

    Dialog(onDismissRequest = onDismiss) {
        EtaSurface(
            modifier = Modifier.widthIn(max = 400.dp),
            color = EtaTheme.colors.surfaceRaised,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
            ) {
                EtaText(text = "Vertrag ändern", style = EtaTheme.typography.title)

                EtaField(label = "Kurzname") {
                    EtaTextField(value = title, onValueChange = { title = it })
                }
                EtaField(label = "Konditionen", hint = "Was verlangt der Vertrag?") {
                    EtaTextField(
                        value = conditions,
                        onValueChange = { conditions = it },
                        singleLine = false,
                    )
                }
                EtaField(label = "Vertragsbruch", hint = "Was gilt als Bruch?") {
                    EtaTextField(
                        value = breach,
                        onValueChange = { breach = it },
                        singleLine = false,
                    )
                }

                EtaField(label = "Aufwand", hint = "Bleibt, wie er ist.") {
                    EtaText(
                        text = effortLabel(contract) + if (LocalPointsVisible.current) {
                            " — ${formatPoints(contract.effort.pointsPerDay)} Punkte am Tag"
                        } else {
                            ""
                        },
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textSecondary,
                    )
                }

                EtaSurface(
                    modifier = Modifier.fillMaxWidth(),
                    color = EtaTheme.colors.surface,
                    borderColor = EtaTheme.colors.warning,
                    contentPadding = EtaTheme.spacing.md,
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        EtaCheckbox(checked = accepted, onCheckedChange = { accepted = it })
                        Spacer(Modifier.width(EtaTheme.spacing.md))
                        EtaText(
                            text = buildString {
                                append("Ich weiß, dass dieser Vertrag nur einmal geändert ")
                                append("werden kann und dass die Laufzeit dabei neu beginnt: ")
                                if (servedDays > 0) {
                                    append("die bisherigen $servedDays Tage verfallen, ")
                                }
                                append("unterschrieben ab ${today.formatLong()}, ")
                                append("Ende ${newEnd.formatLong()}.")
                            },
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textSecondary,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Abbrechen",
                        style = EtaButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    EtaButton(
                        text = "Ändern",
                        enabled = accepted && conditions.isNotBlank(),
                        onClick = { onSave(title.trim(), conditions.trim(), breach.trim()) },
                    )
                }
            }
        }
    }
}

/** Why the long press did nothing, in the one sentence that says which rule applies. */
fun editRefusal(contract: Contract): String? = when {
    contract.state == ContractState.LEGACY ->
        "Legacy-Verträge lassen sich nicht mehr ändern — der Monat, der sie dazu " +
            "gemacht hat, wäre sonst umsonst gewesen."

    // A broken promise being served out is the one thing that must not become a
    // different promise: rewriting it would turn the consequence into a way to
    // pick something easier to keep.
    contract.state == ContractState.PROBATION ->
        "Ein weitergeführter Vertrag lässt sich nicht umschreiben. Nach Ablauf der " +
            "Sperre kannst du ihn neu starten oder aufkündigen."

    contract.state != ContractState.ACTIVE ->
        "Dieser Vertrag ist abgeschlossen."

    contract.editedAt != null ->
        "Dieser Vertrag wurde bereits einmal geändert. Mehr als eine Änderung gibt es nicht."

    else -> null
}

private fun effortLabel(contract: Contract): String = when (contract.effort) {
    ContractEffort.LEICHT -> "Leicht"
    ContractEffort.MITTEL -> "Mittel"
    ContractEffort.SCHWER -> "Schwer"
}
