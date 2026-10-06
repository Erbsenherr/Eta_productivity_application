package com.example.eta.ui.weekplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.eta.ui.attributes.TodoAttributeFields
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.subtasks.FoldCandidate
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.LocalDate

/**
 * Adding something to the Sammelliste without leaving the weekly planning.
 *
 * Created **already concretized**, unlike the dashboard's Quick-Add. A bare note
 * pulled into the week would sit there unplannable — the revolver only offers
 * finished cards — so a goal invented here answers the same questions the
 * concretizing step would have asked anyway. Literally the same questions:
 * `TodoAttributeFields` is that step's own form.
 */
@Composable
fun AddGoalDialog(
    today: LocalDate,
    /** ToDos the new goal can swallow as steps. */
    foldCandidates: List<FoldCandidate> = emptyList(),
    onDismiss: () -> Unit,
    onCreate: (name: String, attributes: TodoAttributes) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var attributes by remember { mutableStateOf(TodoAttributes()) }

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
                EtaText(text = "Neues ToDo", style = EtaTheme.typography.title)
                EtaText(
                    text = "Landet in der Sammelliste und kann gleich in die Woche.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )

                EtaField(label = "Name") {
                    EtaTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Was steht an?",
                    )
                }

                TodoAttributeFields(
                    value = attributes,
                    onChange = { attributes = it },
                    today = today,
                    groupName = name,
                    onGroupName = { name = it },
                    foldCandidates = foldCandidates,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Abbrechen",
                        style = EtaButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    EtaButton(
                        text = "Anlegen",
                        enabled = name.isNotBlank(),
                        onClick = { onCreate(name.trim(), attributes) },
                    )
                }
            }
        }
    }
}
