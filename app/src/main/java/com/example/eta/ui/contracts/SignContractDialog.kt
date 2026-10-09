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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.eta.domain.contract.Signature
import com.example.eta.domain.model.ContractEffort
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaCheckbox
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaStepper
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaSignaturePad
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.theme.EtaTheme
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

/** The same three, for while the points system is out of sight. */
private val EFFORT_OPTIONS_PLAIN = listOf(
    ContractEffort.LEICHT to "Leicht",
    ContractEffort.MITTEL to "Mittel",
    ContractEffort.SCHWER to "Schwer",
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
    var signature by remember { mutableStateOf<Signature?>(null) }
    var weeks by remember { mutableIntStateOf(MIN_WEEKS) }
    var guaranteed by remember { mutableStateOf(false) }

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
                EtaText(text = "Vertrag für Slot ${slot + 1}", style = EtaTheme.typography.title)

                EtaField(label = "Kurzname") {
                    EtaTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = "Wie soll er heißen?",
                    )
                }
                EtaField(label = "Konditionen", hint = "Was verlangt der Vertrag?") {
                    EtaTextField(
                        value = conditions,
                        onValueChange = { conditions = it },
                        placeholder = "Ich verpflichte mich …",
                        singleLine = false,
                    )
                }
                EtaField(label = "Vertragsbruch", hint = "Was gilt als Bruch?") {
                    EtaTextField(
                        value = breach,
                        onValueChange = { breach = it },
                        placeholder = "Gebrochen ist er, wenn …",
                        singleLine = false,
                    )
                }
                EtaField(label = "Aufwand") {
                    EtaChoice(options = EFFORT_OPTIONS, selected = effort, onSelect = { effort = it })
                }
                EtaField(
                    label = "Laufzeit",
                    hint = "Mindestens zwei Wochen. Endet am " +
                        today.plus(DatePeriod(days = weeks * 7)).formatLong() + ".",
                ) {
                    EtaStepper(
                        value = "$weeks Wochen",
                        onDecrement = { weeks = (weeks - 1).coerceAtLeast(MIN_WEEKS) },
                        onIncrement = { weeks += 1 },
                        valueWidth = 96.dp,
                    )
                }

                EtaSurface(
                    modifier = Modifier.fillMaxWidth(),
                    color = EtaTheme.colors.surface,
                    contentPadding = EtaTheme.spacing.md,
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        EtaCheckbox(checked = guaranteed, onCheckedChange = { guaranteed = it })
                        Spacer(Modifier.width(EtaTheme.spacing.md))
                        EtaText(
                            text = "Ich garantiere, die Regeln dieses Vertrags nach bestem " +
                                "Ermessen zu verfolgen, und nehme an, dass ein Vertragsbruch " +
                                "den Vertrag beendet und den Slot für einen Monat ab heute sperrt.",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textSecondary,
                        )
                    }
                }

                EtaField(
                    label = "Unterschrift",
                    hint = "Datum: ${today.formatLong()}",
                ) {
                    EtaSignaturePad(
                        value = signature,
                        onValueChange = { signature = it },
                    )
                }

                if (LocalPointsVisible.current) {
                    EtaText(
                        text = "Ein gehaltener Tag bringt ${formatPoints(effort.pointsPerDay)} Punkte.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }

                if (refusal != null) {
                    EtaText(
                        text = refusal,
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.danger,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Abbrechen",
                        style = EtaButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    EtaButton(
                        text = "Unterschreiben",
                        enabled = guaranteed,
                        // The encoded strokes, or an empty string — which is what
                        // `signContract` refuses with "Es fehlt die Unterschrift."
                        onClick = {
                            onSign(
                                title,
                                conditions,
                                breach,
                                effort,
                                signature?.takeIf { it.isDrawn }?.encode().orEmpty(),
                                weeks,
                            )
                        },
                    )
                }
            }
        }
    }
}
