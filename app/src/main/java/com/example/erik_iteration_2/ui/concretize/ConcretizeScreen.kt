package com.example.erik_iteration_2.ui.concretize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.ItemType
import com.example.erik_iteration_2.ui.attributes.RecurringAttributeFields
import com.example.erik_iteration_2.ui.attributes.RecurringAttributes
import com.example.erik_iteration_2.ui.attributes.TodoAttributeFields
import com.example.erik_iteration_2.ui.attributes.TodoAttributes
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlinx.datetime.LocalDate

/**
 * Phase 2 of the daily planning: the Quick-Add notes get their attributes.
 *
 * One card at a time rather than a long form: each of these is a decision, and a
 * screenful of half-filled rows invites tapping through them without deciding.
 *
 * The questions themselves live in `ui/attributes/`, because this is no longer
 * the only screen that asks them — the Listen-Tab, the weekly planning and the
 * day planner all mount the same two field sets.
 */
@Composable
fun ConcretizeScreen(
    viewModel: ConcretizeViewModel,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()

    ErikScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ErikTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
        ) {
            Column {
                ErikText(text = "Quick-Adds ausfüllen", style = ErikTheme.typography.title)
                ErikText(
                    text = "Ohne Kategorie, Priorität und Dauer lässt sich ein ToDo nicht " +
                        "verplanen — der Revolver bietet nur fertige Karten an. " +
                        "Eine wiederkehrende Notiz braucht Wochentage, Beginn und Dauer, " +
                        "sonst legt sie keinen einzigen Termin an.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textSecondary,
                )
            }

            if (pending.isEmpty()) {
                ErikSurface(modifier = Modifier.fillMaxWidth()) {
                    ErikText(
                        text = "Nichts offen — alle Notizen sind ausgefüllt.",
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.textMuted,
                    )
                }
            }

            // Which card a note gets follows from what it is: the two kinds need
            // different answers, and asking a recurring task for a target date
            // would be asking the wrong question.
            pending.forEach { item ->
                if (item.type == ItemType.RECURRING) {
                    ConcretizeRecurringCard(
                        item = item,
                        onSave = { name, attributes ->
                            viewModel.concretizeRecurring(item, name, attributes)
                        },
                        onDiscard = { viewModel.discard(item) },
                    )
                } else {
                    ConcretizeCard(
                        item = item,
                        today = viewModel.today,
                        onSave = { name, attributes ->
                            viewModel.concretize(item, name, attributes)
                        },
                        onDiscard = { viewModel.discard(item) },
                    )
                }
            }

            ErikButton(
                text = if (pending.isEmpty()) "Weiter zur Tagesplanung" else "Rest später",
                onClick = onDone,
            )

            Spacer(Modifier.size(ErikTheme.spacing.xl))
        }
    }
}

@Composable
private fun ConcretizeCard(
    item: Item,
    today: LocalDate,
    onSave: (String, TodoAttributes) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var attributes by remember(item.id) { mutableStateOf(TodoAttributes.of(item, today)) }

    CardFrame(
        label = null,
        name = name,
        onNameChange = { name = it },
        onDiscard = onDiscard,
        onSave = { onSave(name, attributes) },
    ) {
        TodoAttributeFields(
            value = attributes,
            onChange = { attributes = it },
            today = today,
        )
    }
}

/**
 * The recurring half of the step.
 *
 * No priority and no target date: a standing task has neither. What it does need
 * is exactly the three `expandRecurring` refuses to guess — which days, when, and
 * how long. Several weekdays become several definitions, so that later moving the
 * Tuesday one does not drag the Thursday one along with it.
 */
@Composable
private fun ConcretizeRecurringCard(
    item: Item,
    onSave: (String, RecurringAttributes) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var attributes by remember(item.id) { mutableStateOf(RecurringAttributes.of(item)) }

    CardFrame(
        label = "Wiederkehrend",
        name = name,
        onNameChange = { name = it },
        onDiscard = onDiscard,
        onSave = { onSave(name, attributes) },
        saveEnabled = attributes.weekdays.isNotEmpty(),
    ) {
        RecurringAttributeFields(value = attributes, onChange = { attributes = it })
    }
}

/** The surface, the name field and the two buttons — identical for both cards. */
@Composable
private fun CardFrame(
    label: String?,
    name: String,
    onNameChange: (String) -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    fields: @Composable () -> Unit,
) {
    // Two taps: this button sits beside "Übernehmen" on a form, and a slip here
    // loses the note for good.
    var confirmingDelete by remember { mutableStateOf(false) }

    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            if (label != null) {
                ErikText(
                    text = label,
                    style = ErikTheme.typography.label,
                    color = ErikTheme.colors.accent,
                )
            }
            ErikField(label = "Name") {
                ErikTextField(value = name, onValueChange = onNameChange)
            }

            fields()

            Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                ErikButton(
                    text = if (confirmingDelete) "Wirklich?" else "Löschen",
                    style = ErikButtonStyle.Secondary,
                    onClick = { if (confirmingDelete) onDiscard() else confirmingDelete = true },
                )
                Spacer(Modifier.weight(1f))
                ErikButton(text = "Übernehmen", enabled = saveEnabled, onClick = onSave)
            }
        }
    }
}
