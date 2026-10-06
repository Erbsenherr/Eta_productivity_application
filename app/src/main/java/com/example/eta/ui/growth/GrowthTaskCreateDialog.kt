package com.example.eta.ui.growth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.eta.domain.model.Item
import com.example.eta.domain.recurrence.nextFreeStart
import com.example.eta.domain.recurrence.recurringOverlaps
import com.example.eta.domain.recurrence.weekOccupancy
import com.example.eta.ui.attributes.RecurringAttributeFields
import com.example.eta.ui.attributes.RecurringAttributes
import com.example.eta.ui.attributes.growthIssuesFor
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.subtasks.FoldCandidate
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.theme.EtaTheme

/**
 * "Growth-Task erstellen".
 *
 * The same form a standing task is created with anywhere else, with the
 * Growth-Task box **already ticked** — the button that opened it has said that
 * much. The three lengths and the dynamic switch are under it in the Extras box,
 * where every other sub-feature of a card lives; putting them at the top of this
 * dialog would make them look like a different kind of thing from the same
 * switches seen on the Listen tab.
 *
 * The fit warning is live, so a target length the day cannot hold is said while
 * the form is still open rather than in four weeks.
 */
@Composable
fun GrowthTaskCreateDialog(
    definitions: List<Item>,
    /** ToDos the new task can swallow as steps. */
    foldCandidates: List<FoldCandidate> = emptyList(),
    onDismiss: () -> Unit,
    onCreate: (name: String, note: String?, attributes: RecurringAttributes) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var attributes by remember { mutableStateOf(RecurringAttributes.newGrowth()) }

    val overlaps = remember(attributes, definitions) {
        recurringOverlaps(attributes.slots(), definitions)
    }
    val growthIssues = remember(attributes, definitions) {
        // No order yet: it will be last in the tab, which is the weakest claim on
        // a contested slot — so the check is made on those terms.
        growthIssuesFor(attributes = attributes, definitions = definitions, order = null)
    }

    EtaDialog(title = "Neue Growth-Task", onDismiss = onDismiss) {
        EtaText(
            text = "Beginnt bei der Startzeit und wird mit jedem bestätigten Abschluss " +
                "um das Inkrement länger, bis die Zielzeit erreicht ist.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.accent,
        )

        EtaField(label = "Name") {
            EtaTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Woran wächst du?",
            )
        }
        EtaField(label = "Notiz") {
            EtaTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "Was gehört dazu?",
                singleLine = false,
            )
        }

        RecurringAttributeFields(
            value = attributes,
            onChange = { attributes = it },
            overlaps = overlaps,
            // No setup on this tab, so the night is not an obstacle here.
            findNextFree = { nextFreeStart(it.slots(), weekOccupancy(definitions, null)) },
            allowGrowth = true,
            growthIssues = growthIssues,
            groupName = name,
            onGroupName = { name = it },
            foldCandidates = foldCandidates,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = "Erstellen",
                enabled = name.isNotBlank() && attributes.weekdays.isNotEmpty(),
                onClick = {
                    onCreate(name.trim(), note.trim().ifBlank { null }, attributes)
                },
            )
        }
    }
}
