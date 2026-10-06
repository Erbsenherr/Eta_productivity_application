package com.example.eta.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.GroupAnswers
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.subtask.MergePart
import com.example.eta.domain.subtask.drafts
import com.example.eta.domain.subtask.foldedWith
import com.example.eta.domain.subtask.mergedDuration
import com.example.eta.domain.subtask.mergedGroup
import com.example.eta.domain.subtask.orderedParts
import com.example.eta.ui.attributes.CategoryField
import com.example.eta.ui.attributes.MIN_TASK_DURATION
import com.example.eta.ui.attributes.PriorityField
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.subtasks.FoldCandidate
import com.example.eta.ui.subtasks.SubtaskBuilderDialog
import com.example.eta.ui.subtasks.SubtaskSetting
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * What a block contributes when it is folded into a group: its name, its note and
 * what it cost. Everything else it carried is gone — see [MergePart].
 */
private fun BlockWithItem.mergePart() = MergePart(
    name = item.name,
    note = item.note,
    duration = block.effectiveDuration,
    travelBefore = block.travelBefore,
    returnAfter = block.returnAfter,
    breakAfter = block.breakAfter,
)

/** Everything the merge destroys, named before it happens. */
private fun BlockWithItem.lostAttributes(): List<String> = listOfNotNull(
    item.category?.let { "Kategorie" },
    item.priority?.let { "Priorität" },
    "Anfahrt/Pause".takeIf {
        block.travelBefore != null || block.returnAfter != null || block.breakAfter != null
    },
    "Erinnerung".takeIf { item.reminderLeadHours != null },
    "Pomodoro".takeIf { item.pomodoroWork != null },
    "Wachstum".takeIf { item.growthTarget != null },
    "Deadline".takeIf { item.deadlineAt != null },
)

/**
 * The question a drop on another block asks, in the three shapes it comes in.
 *
 * All three offer **Nur verschieben**: dropping onto occupied time to reach the
 * next free slot is a gesture the planner has always had, and a dense day needs
 * it. Losing it to the new one would be a bad trade.
 */
@Composable
fun MergePromptDialog(
    dragged: BlockWithItem,
    target: BlockWithItem,
    draggedSubtasks: List<Subtask>,
    targetSubtasks: List<Subtask>,
    onDismiss: () -> Unit,
    onMoveOnly: () -> Unit,
    /** Two plain tasks: the full builder comes next. */
    onBuildGroup: () -> Unit,
    /** One of them is already a group: [survivor] keeps its list, the other joins it. */
    onFold: (survivor: BlockWithItem, dissolved: BlockWithItem, deadline: Instant?) -> Unit,
) {
    val draggedIsGroup = draggedSubtasks.isNotEmpty()
    val targetIsGroup = targetSubtasks.isNotEmpty()

    when {
        !draggedIsGroup && !targetIsGroup -> EtaDialog(
            title = "Tasks gruppieren?",
            onDismiss = onDismiss,
        ) {
            EtaText(
                text = "»${dragged.item.name}« und »${target.item.name}« werden eine Aufgabe " +
                    "mit zwei Schritten. Die Dauern werden addiert; die Wege dazwischen fallen " +
                    "weg.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
            LostNote(dragged)
            // The answer on a line of its own: three buttons in one row are wider
            // than a phone, and the one pushed out of sight was the confirmation.
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(
                    text = "Gruppieren",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBuildGroup,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Abbrechen",
                        style = EtaButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    EtaButton(
                        text = "Nur verschieben",
                        style = EtaButtonStyle.Secondary,
                        onClick = onMoveOnly,
                    )
                }
            }
        }

        draggedIsGroup && targetIsGroup -> {
            var deadline by remember(dragged.block.id, target.block.id) {
                mutableStateOf(target.item.deadlineAt ?: dragged.item.deadlineAt)
            }
            EtaDialog(title = "Gruppen zusammenlegen?", onDismiss = onDismiss) {
                EtaText(
                    text = "Beide sind Gruppen. Eine muss aufgelöst werden — ihre Schritte " +
                        "wandern in die andere, und ihre Zeit kommt dem Tag zurück.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
                DeadlineChoice(
                    a = target,
                    b = dragged,
                    selected = deadline,
                    onSelect = { deadline = it },
                )
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "»${dragged.item.name}« auflösen",
                        onClick = { onFold(target, dragged, deadline) },
                    )
                    EtaButton(
                        text = "»${target.item.name}« auflösen",
                        onClick = { onFold(dragged, target, deadline) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        EtaButton(
                            text = "Abbrechen",
                            style = EtaButtonStyle.Secondary,
                            onClick = onDismiss,
                        )
                        Spacer(Modifier.weight(1f))
                        EtaButton(
                            text = "Nur verschieben",
                            style = EtaButtonStyle.Secondary,
                            onClick = onMoveOnly,
                        )
                    }
                }
            }
        }

        else -> {
            val group = if (draggedIsGroup) dragged else target
            val joining = if (draggedIsGroup) target else dragged
            var deadline by remember(dragged.block.id, target.block.id) {
                mutableStateOf(group.item.deadlineAt ?: joining.item.deadlineAt)
            }
            EtaDialog(title = "Zur Gruppe hinzufügen?", onDismiss = onDismiss) {
                EtaText(
                    text = "»${group.item.name}« hat schon Schritte. »${joining.item.name}« " +
                        "wird ein weiterer und verlängert die Gruppe um " +
                        "${joining.block.effectiveDuration.formatShort()}.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
                LostNote(joining)
                DeadlineChoice(
                    a = group,
                    b = joining,
                    selected = deadline,
                    onSelect = { deadline = it },
                )
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Hinzufügen",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onFold(group, joining, deadline) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        EtaButton(
                            text = "Abbrechen",
                            style = EtaButtonStyle.Secondary,
                            onClick = onDismiss,
                        )
                        Spacer(Modifier.weight(1f))
                        EtaButton(
                            text = "Nur verschieben",
                            style = EtaButtonStyle.Secondary,
                            onClick = onMoveOnly,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Which deadline the group keeps, asked only when both sides carry one.
 *
 * Confirmed with the user: a subtask cannot have a deadline, so one of the two has
 * to go, and the app does not pick. Where only one side has a deadline the group
 * simply inherits it and there is nothing to ask.
 */
@Composable
private fun DeadlineChoice(
    a: BlockWithItem,
    b: BlockWithItem,
    selected: Instant?,
    onSelect: (Instant?) -> Unit,
) {
    val first = a.item.deadlineAt ?: return
    val second = b.item.deadlineAt ?: return
    val zone = TimeZone.currentSystemDefault()

    EtaField(
        label = "Deadline",
        hint = "Nur die Gruppe kann eine haben — beide Tasks bringen eine mit.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
            listOf(
                first to "»${a.item.name}«: ${first.toLocalDateTime(zone).date.formatLong()}",
                second to "»${b.item.name}«: ${second.toLocalDateTime(zone).date.formatLong()}",
                null to "Keine behalten",
            ).forEach { (value, label) ->
                EtaButton(
                    text = label,
                    style = if (value == selected) {
                        EtaButtonStyle.Primary
                    } else {
                        EtaButtonStyle.Secondary
                    },
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

/** What the task being folded in loses by becoming a step. */
@Composable
private fun LostNote(dissolved: BlockWithItem) {
    val lost = dissolved.lostAttributes()
    if (lost.isEmpty()) return
    EtaText(
        text = "»${dissolved.item.name}« wird auf Name und Notiz reduziert. Verworfen wird: " +
            lost.joinToString(", ") + ".",
        style = EtaTheme.typography.caption,
        color = EtaTheme.colors.warning,
    )
}

/**
 * "Tasks gruppieren?", in full: the builder plus the answers a new group owes.
 *
 * The name, the order and the steps are the builder's own; category, priority and
 * duration are asked here because they decide what the group *earns*, and letting
 * the app pick would quietly halve or double the yield of two tasks that had
 * different categories. Suggested from the task that was dropped **on** — it is
 * the one keeping its place in the day.
 */
@Composable
fun GroupTasksDialog(
    dragged: BlockWithItem,
    target: BlockWithItem,
    foldCandidates: List<FoldCandidate> = emptyList(),
    onDismiss: () -> Unit,
    onGroup: (GroupAnswers) -> Unit,
) {
    val parts = remember(dragged.block.id, target.block.id) {
        listOf(target.mergePart(), dragged.mergePart())
    }
    val suggested = remember(parts) { mergedGroup(parts) }

    var category by remember(parts) { mutableStateOf(target.item.category ?: Category.FOKUS) }
    var priority by remember(parts) { mutableStateOf(target.item.priority ?: Priority.MUST) }
    var duration by remember(parts) { mutableStateOf(suggested.duration) }
    var deadline by remember(parts) {
        mutableStateOf(target.item.deadlineAt ?: dragged.item.deadlineAt)
    }

    SubtaskBuilderDialog(
        title = "Tasks gruppieren?",
        saveLabel = "Gruppieren",
        setting = SubtaskSetting(groupName = target.item.name, subtasks = suggested.subtasks),
        candidates = foldCandidates,
        onDismiss = onDismiss,
        onSave = { setting ->
            // The margins follow whichever of the two ended up first and last, so
            // reordering in the builder is honoured — matched by name, the only
            // handle a draft that has never been saved has.
            val ordered = mergedGroup(orderedParts(parts, setting.subtasks))
            onGroup(
                GroupAnswers(
                    name = setting.groupName,
                    subtasks = setting.subtasks,
                    duration = duration,
                    category = category,
                    priority = priority,
                    deadlineAt = deadline,
                    travelBefore = ordered.travelBefore,
                    returnAfter = ordered.returnAfter,
                    breakAfter = ordered.breakAfter,
                ),
            )
        },
        footer = {
            CategoryField(value = category, onChange = { category = it })
            PriorityField(value = priority, onChange = { priority = it })
            EtaField(
                label = "Dauer",
                hint = "Vorgeschlagen ist die Summe der beiden Aufgaben ohne ihre Wege.",
            ) {
                EtaDurationPicker(
                    value = duration,
                    onValueChange = { duration = it },
                    minimum = MIN_TASK_DURATION,
                )
            }
            DeadlineChoice(
                a = target,
                b = dragged,
                selected = deadline,
                onSelect = { deadline = it },
            )
            LostNote(dragged)
        },
    )
}

/**
 * The answers the two shorter questions work out on their own.
 *
 * The survivor keeps everything it has — its category, its priority, its own
 * margins and its hour — and grows by the folded task's **pure** duration. That is
 * the same rule the full merge follows, with nobody left to ask.
 */
fun foldAnswers(
    survivor: BlockWithItem,
    survivorSubtasks: List<Subtask>,
    dissolved: BlockWithItem,
    dissolvedSubtasks: List<Subtask>,
    deadlineAt: Instant?,
): GroupAnswers = GroupAnswers(
    name = survivor.item.name,
    subtasks = if (dissolvedSubtasks.isEmpty()) {
        survivorSubtasks.foldedWith(dissolved.item.name, dissolved.item.note)
    } else {
        survivorSubtasks.foldedWith(dissolvedSubtasks.drafts())
    },
    duration = mergedDuration(
        listOf(survivor.block.effectiveDuration, dissolved.block.effectiveDuration),
    ),
    category = survivor.item.category,
    priority = survivor.item.priority,
    deadlineAt = deadlineAt,
    travelBefore = survivor.block.travelBefore,
    returnAfter = survivor.block.returnAfter,
    breakAfter = survivor.block.breakAfter,
)
