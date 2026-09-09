package com.example.erik_iteration_2.ui.contracts

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
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.ContractEffort
import com.example.erik_iteration_2.domain.model.ContractState
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikCheckbox
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.format.formatPoints
import com.example.erik_iteration_2.ui.theme.ErikTheme
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
        ErikSurface(
            modifier = Modifier.widthIn(max = 400.dp),
            color = ErikTheme.colors.surfaceRaised,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
            ) {
                ErikText(text = "Vertrag ändern", style = ErikTheme.typography.title)

                ErikField(label = "Kurzname") {
                    ErikTextField(value = title, onValueChange = { title = it })
                }
                ErikField(label = "Konditionen", hint = "Was verlangt der Vertrag?") {
                    ErikTextField(
                        value = conditions,
                        onValueChange = { conditions = it },
                        singleLine = false,
                    )
                }
                ErikField(label = "Vertragsbruch", hint = "Was gilt als Bruch?") {
                    ErikTextField(
                        value = breach,
                        onValueChange = { breach = it },
                        singleLine = false,
                    )
                }

                ErikField(label = "Aufwand", hint = "Bleibt, wie er ist.") {
                    ErikText(
                        text = "${effortLabel(contract)} — " +
                            "${formatPoints(contract.effort.pointsPerDay)} Punkte am Tag",
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.textSecondary,
                    )
                }

                ErikSurface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ErikTheme.colors.surface,
                    borderColor = ErikTheme.colors.warning,
                    contentPadding = ErikTheme.spacing.md,
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        ErikCheckbox(checked = accepted, onCheckedChange = { accepted = it })
                        Spacer(Modifier.width(ErikTheme.spacing.md))
                        ErikText(
                            text = buildString {
                                append("Ich weiß, dass dieser Vertrag nur einmal geändert ")
                                append("werden kann und dass die Laufzeit dabei neu beginnt: ")
                                if (servedDays > 0) {
                                    append("die bisherigen $servedDays Tage verfallen, ")
                                }
                                append("unterschrieben ab ${today.formatLong()}, ")
                                append("Ende ${newEnd.formatLong()}.")
                            },
                            style = ErikTheme.typography.caption,
                            color = ErikTheme.colors.textSecondary,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                    ErikButton(
                        text = "Abbrechen",
                        style = ErikButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    ErikButton(
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
