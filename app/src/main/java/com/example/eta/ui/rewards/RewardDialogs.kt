package com.example.eta.ui.rewards

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import com.example.eta.domain.recurrence.RecurringGroup
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaCheckbox
import com.example.eta.ui.components.EtaCountPicker
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.lists.recurringSummary
import com.example.eta.ui.theme.EtaTheme

/** What a reward costs until someone says otherwise, and the most it may. */
const val DEFAULT_REWARD_COST = 25
const val MAX_REWARD_COST = 9999

/**
 * A reward being made or changed.
 *
 * [itemIds] are the rows of the standing tasks it is to be bound to — every row
 * of each ticked task, since a task is one row per weekday.
 */
data class RewardDraft(
    val id: String? = null,
    val name: String = "",
    val cost: Int = DEFAULT_REWARD_COST,
    val itemIds: Set<String> = emptySet(),
) {
    companion object {
        /**
         * The draft an existing reward opens with.
         *
         * The binding is read by name and written back as the rows the tasks
         * have **now**, so saving also brings it up to date with weekdays added
         * since it was made.
         */
        fun of(row: RewardRow, state: RewardsUiState) = RewardDraft(
            id = row.reward.id,
            name = row.reward.name,
            cost = row.reward.cost.toInt().coerceIn(1, MAX_REWARD_COST),
            itemIds = state.groups
                .filter { it.isBoundBy(row.entry.boundNames) }
                .flatMapTo(mutableSetOf()) { it.ids },
        )
    }
}

/** Name, price, and the way into the binding menu. */
@Composable
internal fun RewardEditDialog(
    draft: RewardDraft,
    groups: List<RecurringGroup>,
    onChange: (RewardDraft) -> Unit,
    onBind: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val bound = groups.filter { group -> group.ids.any { it in draft.itemIds } }
        .map { it.representative.name }
        .distinct()

    EtaDialog(
        title = if (draft.id == null) "Neue Belohnung" else "Belohnung",
        onDismiss = onDismiss,
    ) {
        EtaField(label = "Name") {
            EtaTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                placeholder = "Ein gutes Steak für 50 Euro",
            )
        }
        EtaField(
            label = "Preis in Punkten",
            hint = "Antippen, um die Zahl einzutippen.",
        ) {
            EtaCountPicker(
                value = draft.cost,
                onValueChange = { onChange(draft.copy(cost = it)) },
                range = 1..MAX_REWARD_COST,
                title = "Preis eingeben",
            )
        }
        EtaField(
            label = "Aufgabenbindung",
            hint = if (bound.isEmpty()) {
                "Keine — jede abgehakte Aufgabe füllt diese Belohnung."
            } else {
                "Nur diese füllen sie: ${bound.joinToString(", ")}."
            },
        ) {
            EtaButton(
                text = "Aufgabenbindung",
                style = EtaButtonStyle.Secondary,
                onClick = onBind,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = if (draft.id == null) "Erstellen" else "Sichern",
                enabled = draft.name.isNotBlank(),
                onClick = onSave,
            )
        }
    }
}

/**
 * The binding menu: the standing schedule, one line per task, to be ticked.
 *
 * Nothing ticked is an answer of its own — the reward then counts every finished
 * task — and the menu says so rather than leaving an empty list to be guessed at.
 * Closing it by tapping outside is the same as "Fertig": the ticks are already in
 * the draft, and there is nothing here to cancel that the reward's own "Abbrechen"
 * does not cancel.
 */
@Composable
internal fun BindingDialog(
    groups: List<RecurringGroup>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    onNewTask: () -> Unit,
    onDone: () -> Unit,
) {
    EtaDialog(title = "Aufgabenbindung", onDismiss = onDone) {
        EtaText(
            text = "Angehakt füllen nur diese wiederkehrenden Aufgaben die Belohnung. " +
                "Ohne Haken zählt jede abgehakte Aufgabe.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )

        if (groups.isEmpty()) {
            EtaText(
                text = "Noch keine wiederkehrenden Aufgaben.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textMuted,
            )
        }
        groups.forEach { group ->
            key(group.representative.id) {
                val checked = group.ids.any { it in selected }
                BindingRow(
                    group = group,
                    checked = checked,
                    onToggle = {
                        onChange(if (checked) selected - group.ids else selected + group.ids)
                    },
                )
            }
        }

        EtaButton(
            text = "Neue wiederkehrende Aufgabe",
            style = EtaButtonStyle.Secondary,
            onClick = onNewTask,
        )
        Row {
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Fertig", onClick = onDone)
        }
    }
}

/** One standing task in the menu; the whole line ticks, not only the box. */
@Composable
private fun BindingRow(group: RecurringGroup, checked: Boolean, onToggle: () -> Unit) {
    val toggle = rememberUpdatedState(onToggle)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(group.representative.id) {
                detectTapGestures(onTap = { toggle.value() })
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EtaCheckbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.size(EtaTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            EtaText(
                text = group.representative.name,
                style = EtaTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            EtaText(
                text = recurringSummary(group),
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
