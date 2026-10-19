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
import com.example.eta.domain.reward.SleepBonus
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
 * A reward being made. There is no draft of an existing one: a reward is not
 * changed once it stands (step 47).
 *
 * [itemIds] are the rows of the standing tasks it is to be bound to — every row
 * of each ticked task, since a task is one row per weekday.
 */
data class RewardDraft(
    val name: String = "",
    val cost: Int = DEFAULT_REWARD_COST,
    val itemIds: Set<String> = emptySet(),
)

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
        .distinct() +
        SleepBonus.entries.filter { it.itemId in draft.itemIds }.map { it.label }

    EtaDialog(title = "Neue Belohnung", onDismiss = onDismiss) {
        EtaText(
            text = "Name, Preis und Bindung stehen danach fest — eine Belohnung lässt " +
                "sich später nur noch löschen.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        EtaField(label = "Name") {
            EtaTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                placeholder = "Ein großer Eisbecher in der Eisdiele",
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
                text = "Erstellen",
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
    /** Tasks another reward holds, by normalized name → that reward's name. */
    taken: Map<String, String> = emptyMap(),
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    onNewTask: () -> Unit,
    onDone: () -> Unit,
) {
    EtaDialog(title = "Aufgabenbindung", onDismiss = onDone) {
        EtaText(
            text = "Angehakt füllen nur diese wiederkehrenden Aufgaben die Belohnung — " +
                "an jeder Stelle der Liste. Eine Aufgabe gehört zu genau einer " +
                "Belohnung und zählt dann für keine andere. Ohne Haken zählt jede " +
                "abgehakte Aufgabe, die nirgends gebunden ist.",
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
                // Held by another reward: shown, and said whose it is, but not
                // to be ticked here — it would have to be unticked there first.
                val heldBy = taken[group.representative.normalizedName].takeUnless { checked }
                BindingRow(
                    group = group,
                    checked = checked,
                    heldBy = heldBy,
                    onToggle = {
                        if (heldBy == null) {
                            onChange(if (checked) selected - group.ids else selected + group.ids)
                        }
                    },
                )
            }
        }

        EtaButton(
            text = "Neue wiederkehrende Aufgabe",
            style = EtaButtonStyle.Secondary,
            onClick = onNewTask,
        )

        // The two half points of the evening (step 48): no task behind them,
        // but bound and held exactly like one.
        EtaText(text = "Schlaf", style = EtaTheme.typography.bodyStrong)
        SleepBonus.entries.forEach { bonus ->
            key(bonus.itemId) {
                val checked = bonus.itemId in selected
                val heldBy = taken[bonus.taskName].takeUnless { checked }
                BindingLine(
                    id = bonus.itemId,
                    title = bonus.label,
                    subtitle = heldBy?.let { "Gebunden an »$it«" } ?: "Der halbe Punkt am Abend",
                    checked = checked,
                    enabled = heldBy == null,
                    onToggle = {
                        if (heldBy == null) {
                            onChange(if (checked) selected - bonus.itemId else selected + bonus.itemId)
                        }
                    },
                )
            }
        }
        Row {
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Fertig", onClick = onDone)
        }
    }
}

/** One standing task in the menu; the whole line ticks, not only the box. */
@Composable
private fun BindingRow(
    group: RecurringGroup,
    checked: Boolean,
    heldBy: String?,
    onToggle: () -> Unit,
) {
    BindingLine(
        id = group.representative.id,
        title = group.representative.name,
        subtitle = if (heldBy != null) "Gebunden an »$heldBy«" else recurringSummary(group),
        checked = checked,
        enabled = heldBy == null,
        onToggle = onToggle,
    )
}

/** A line of the binding menu: a box, a name and what is under it. */
@Composable
private fun BindingLine(
    id: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val toggle = rememberUpdatedState(onToggle)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(id) {
                detectTapGestures(onTap = { toggle.value() })
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EtaCheckbox(checked = checked, enabled = enabled, onCheckedChange = { onToggle() })
        Spacer(Modifier.size(EtaTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            EtaText(
                text = title,
                style = EtaTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            EtaText(
                text = subtitle,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
