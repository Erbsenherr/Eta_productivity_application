package com.example.eta.ui.concretize

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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.recurrence.nextFreeStart
import com.example.eta.domain.recurrence.recurringOverlaps
import com.example.eta.domain.recurrence.weekOccupancy
import com.example.eta.ui.attributes.RecurringAttributeFields
import com.example.eta.ui.attributes.RecurringAttributes
import com.example.eta.ui.attributes.growthIssuesFor
import com.example.eta.ui.attributes.TodoAttributeFields
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.components.LocalTutorialGuide
import com.example.eta.ui.components.tutorialAllows
import com.example.eta.ui.components.tutorialSpot
import com.example.eta.domain.tutorial.TutorialGate
import com.example.eta.ui.lists.SchemePreview
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.tutorial.TutorialSpot
import com.example.eta.ui.subtasks.FoldCandidate
import com.example.eta.ui.subtasks.foldCandidatesExcept
import com.example.eta.ui.theme.EtaTheme
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
    val definitions by viewModel.definitions.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    val foldable by viewModel.foldable.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()

    EtaScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            Column {
                EtaText(text = "Quick-Adds ausfüllen", style = EtaTheme.typography.title)
                EtaText(
                    text = "Ohne Kategorie, Priorität und Dauer lässt sich ein ToDo nicht " +
                        "verplanen — der Revolver bietet nur fertige Karten an. " +
                        "Eine wiederkehrende Notiz braucht Wochentage, Beginn und Dauer, " +
                        "sonst legt sie keinen einzigen Termin an.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            if (pending.isEmpty()) {
                EtaSurface(modifier = Modifier.fillMaxWidth()) {
                    EtaText(
                        text = "Nichts offen — alle Notizen sind ausgefüllt.",
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            }

            // Which card a note gets follows from what it is: the two kinds need
            // different answers, and asking a recurring task for a target date
            // would be asking the wrong question.
            //
            // Keyed by id, not by position. Without it every `remember` inside a
            // card belongs to its *slot*: deleting one card moved the next into
            // that slot, and it arrived already showing "Wirklich?" — one tap from
            // being deleted as well.
            pending.forEach { item ->
                key(item.id) {
                    if (item.type == ItemType.RECURRING) {
                        ConcretizeRecurringCard(
                            item = item,
                            definitions = definitions,
                            setup = setup,
                            subtasks = subtasks[item.id].orEmpty(),
                            foldCandidates = foldable.foldCandidatesExcept(item.id),
                            onSave = { name, attributes ->
                                viewModel.concretizeRecurring(item, name, attributes)
                            },
                            onDiscard = { viewModel.discard(item) },
                        )
                    } else {
                        ConcretizeCard(
                            item = item,
                            today = viewModel.today,
                            subtasks = subtasks[item.id].orEmpty(),
                            foldCandidates = foldable.foldCandidatesExcept(item.id),
                            onSave = { name, attributes ->
                                viewModel.concretize(item, name, attributes)
                            },
                            onDiscard = { viewModel.discard(item) },
                        )
                    }
                }
            }

            EtaButton(
                text = if (pending.isEmpty()) "Weiter zur Tagesplanung" else "Rest später",
                enabled = tutorialAllows(TutorialGate.CONCRETIZE_DONE),
                onClick = onDone,
            )
            // Only in the tutorial, at the step that would otherwise leave the
            // user looking at a grey button with no word about why.
            if (LocalTutorialGuide.current?.allowed?.contains(TutorialGate.CONCRETIZE_LATER_NOTE) == true) {
                EtaText(
                    text = "Im Tutorial kommen wir später zur Tagesplanung — zuerst " +
                        "folgt die Wochenplanung.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }

            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }
}

@Composable
private fun ConcretizeCard(
    item: Item,
    today: LocalDate,
    subtasks: List<Subtask>,
    foldCandidates: List<FoldCandidate>,
    onSave: (String, TodoAttributes) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var attributes by remember(item.id) { mutableStateOf(TodoAttributes.of(item, today, subtasks)) }

    CardFrame(
        label = null,
        name = name,
        onNameChange = { name = it },
        onDiscard = onDiscard,
        onSave = { onSave(name, attributes) },
        saveEnabled = tutorialAllows(TutorialGate.CONCRETIZE_TODO),
        saveSpot = TutorialSpot.TODO_SAVE,
    ) {
        TodoAttributeFields(
            value = attributes,
            onChange = { attributes = it },
            today = today,
            groupName = name,
            onGroupName = { name = it },
            foldCandidates = foldCandidates,
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
    definitions: List<Item>,
    setup: UserSetup?,
    subtasks: List<Subtask>,
    foldCandidates: List<FoldCandidate>,
    onSave: (String, RecurringAttributes) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var attributes by remember(item.id) { mutableStateOf(RecurringAttributes.of(item, subtasks)) }

    CardFrame(
        label = "Wiederkehrend",
        name = name,
        onNameChange = { name = it },
        onDiscard = onDiscard,
        onSave = { onSave(name, attributes) },
        saveEnabled = attributes.weekdays.isNotEmpty() &&
            tutorialAllows(TutorialGate.CONCRETIZE_RECURRING),
        saveSpot = TutorialSpot.RECURRING_SAVE,
    ) {
        // Against the standing schedule as it is now, so a Tuesday evening that
        // is already taken says so before the note becomes a second one.
        val overlaps = remember(attributes, definitions) {
            recurringOverlaps(attributes.slots(), definitions, ignoreIds = setOf(item.id))
        }
        val growthIssues = remember(attributes, definitions) {
            growthIssuesFor(
                attributes = attributes,
                definitions = definitions,
                ignoreIds = setOf(item.id),
            )
        }
        // The week without this note, and the note drawn over it as the answers
        // come in — the same sketch the Listen tab's editor brings along, here
        // because this is where a standing task is first given its hours.
        val booked = remember(definitions, setup, item.id) {
            weekOccupancy(definitions, setup, ignoreIds = setOf(item.id))
        }
        SchemePreview(
            booked = booked,
            attributes = attributes,
            overlaps = overlaps,
            modifier = Modifier.tutorialSpot(TutorialSpot.RECURRING_SCHEME),
        )
        RecurringAttributeFields(
            value = attributes,
            onChange = { attributes = it },
            overlaps = overlaps,
            findNextFree = { nextFreeStart(it.slots(), booked) },
            allowGrowth = true,
            growthIssues = growthIssues,
            groupName = name,
            onGroupName = { name = it },
            foldCandidates = foldCandidates,
        )
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
    /** The id the tutorial frames "Übernehmen" by, one per kind of card. */
    saveSpot: String = "",
    fields: @Composable () -> Unit,
) {
    // Two taps: this button sits beside "Übernehmen" on a form, and a slip here
    // loses the note for good.
    var confirmingDelete by remember { mutableStateOf(false) }

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            if (label != null) {
                EtaText(
                    text = label,
                    style = EtaTheme.typography.label,
                    color = EtaTheme.colors.accent,
                )
            }
            EtaField(label = "Name") {
                EtaTextField(value = name, onValueChange = onNameChange)
            }

            fields()

            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(
                    text = if (confirmingDelete) "Wirklich?" else "Löschen",
                    style = EtaButtonStyle.Secondary,
                    // A note thrown away mid-tutorial leaves it nothing to plan.
                    enabled = tutorialAllows(TutorialGate.CONCRETIZE_DELETE),
                    onClick = { if (confirmingDelete) onDiscard() else confirmingDelete = true },
                )
                Spacer(Modifier.weight(1f))
                EtaButton(
                    text = "Übernehmen",
                    enabled = saveEnabled,
                    onClick = onSave,
                    modifier = Modifier.tutorialSpot(saveSpot),
                )
            }
        }
    }
}
