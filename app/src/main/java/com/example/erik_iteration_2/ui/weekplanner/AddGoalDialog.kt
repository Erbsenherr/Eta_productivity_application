package com.example.erik_iteration_2.ui.weekplanner

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
import com.example.erik_iteration_2.ui.attributes.TodoAttributeFields
import com.example.erik_iteration_2.ui.attributes.TodoAttributes
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.theme.ErikTheme
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
    onDismiss: () -> Unit,
    onCreate: (name: String, attributes: TodoAttributes) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var attributes by remember { mutableStateOf(TodoAttributes()) }

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
                ErikText(text = "Neues ToDo", style = ErikTheme.typography.title)
                ErikText(
                    text = "Landet in der Sammelliste und kann gleich in die Woche.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                )

                ErikField(label = "Name") {
                    ErikTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Was steht an?",
                    )
                }

                TodoAttributeFields(
                    value = attributes,
                    onChange = { attributes = it },
                    today = today,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                    ErikButton(
                        text = "Abbrechen",
                        style = ErikButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    ErikButton(
                        text = "Anlegen",
                        enabled = name.isNotBlank(),
                        onClick = { onCreate(name.trim(), attributes) },
                    )
                }
            }
        }
    }
}
