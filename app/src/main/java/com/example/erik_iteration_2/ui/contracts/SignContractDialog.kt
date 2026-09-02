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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.erik_iteration_2.domain.model.ContractEffort
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikCheckbox
import com.example.erik_iteration_2.ui.components.ErikChoice
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikStepper
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.format.formatPoints
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Shortest term the concept allows, in weeks. */
private const val MIN_WEEKS = 2

private val EFFORT_OPTIONS = listOf(
    ContractEffort.LEICHT to "Leicht — 0,5 Punkte am Tag",
    ContractEffort.MITTEL to "Mittel — 1 Punkt am Tag",
    ContractEffort.SCHWER to "Schwer — 1,5 Punkte am Tag",
)

/**
 * Signing a contract with oneself.
 *
 * The guarantee and the signature are not decoration: the concept builds the
 * mechanism on having promised, so the dialog states what a breach costs *before*
 * the signature field, and will not sign without it.
 */
@Composable
fun SignContractDialog(
    slot: Int,
    today: LocalDate,
    refusal: String?,
    onDismiss: () -> Unit,
    onSign: (
        title: String,
        conditions: String,
        breach: String,
        effort: ContractEffort,
        signature: String,
        weeks: Int,
    ) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var conditions by remember { mutableStateOf("") }
    var breach by remember { mutableStateOf("") }
    var effort by remember { mutableStateOf(ContractEffort.MITTEL) }
    var signature by remember { mutableStateOf("") }
    var weeks by remember { mutableIntStateOf(MIN_WEEKS) }
    var guaranteed by remember { mutableStateOf(false) }

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
                ErikText(text = "Vertrag für Slot ${slot + 1}", style = ErikTheme.typography.title)

                ErikField(label = "Kurzname") {
                    ErikTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = "Wie soll er heißen?",
                    )
                }
                ErikField(label = "Konditionen", hint = "Was verlangt der Vertrag?") {
                    ErikTextField(
                        value = conditions,
                        onValueChange = { conditions = it },
                        placeholder = "Ich verpflichte mich …",
                        singleLine = false,
                    )
                }
                ErikField(label = "Vertragsbruch", hint = "Was gilt als Bruch?") {
                    ErikTextField(
                        value = breach,
                        onValueChange = { breach = it },
                        placeholder = "Gebrochen ist er, wenn …",
                        singleLine = false,
                    )
                }
                ErikField(label = "Aufwand") {
                    ErikChoice(options = EFFORT_OPTIONS, selected = effort, onSelect = { effort = it })
                }
                ErikField(
                    label = "Laufzeit",
                    hint = "Mindestens zwei Wochen. Endet am " +
                        today.plus(DatePeriod(days = weeks * 7)).formatLong() + ".",
                ) {
                    ErikStepper(
                        value = "$weeks Wochen",
                        onDecrement = { weeks = (weeks - 1).coerceAtLeast(MIN_WEEKS) },
                        onIncrement = { weeks += 1 },
                        valueWidth = 96.dp,
                    )
                }

                ErikSurface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ErikTheme.colors.surface,
                    contentPadding = ErikTheme.spacing.md,
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        ErikCheckbox(checked = guaranteed, onCheckedChange = { guaranteed = it })
                        Spacer(Modifier.width(ErikTheme.spacing.md))
                        ErikText(
                            text = "Ich garantiere, die Regeln dieses Vertrags nach bestem " +
                                "Ermessen zu verfolgen, und nehme an, dass ein Vertragsbruch " +
                                "den Vertrag beendet und den Slot für einen Monat ab heute sperrt.",
                            style = ErikTheme.typography.caption,
                            color = ErikTheme.colors.textSecondary,
                        )
                    }
                }

                ErikField(
                    label = "Unterschrift",
                    hint = "Datum: ${today.formatLong()}",
                ) {
                    ErikTextField(
                        value = signature,
                        onValueChange = { signature = it },
                        placeholder = "Unterschreiben",
                    )
                }

                ErikText(
                    text = "Ein gehaltener Tag bringt ${formatPoints(effort.pointsPerDay)} Punkte.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                )

                if (refusal != null) {
                    ErikText(
                        text = refusal,
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.danger,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                    ErikButton(
                        text = "Abbrechen",
                        style = ErikButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    ErikButton(
                        text = "Unterschreiben",
                        enabled = guaranteed,
                        onClick = { onSign(title, conditions, breach, effort, signature, weeks) },
                    )
                }
            }
        }
    }
}
