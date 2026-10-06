package com.example.eta.domain.model

import com.example.eta.domain.growth.QuantitySetting
import com.example.eta.domain.growth.quantitySetting
import com.example.eta.domain.growth.withQuantity
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * What the "Extras" box adds to a card, gathered into one value.
 *
 * The journeys, the break and `task_end.mp3` predate the box and already travel
 * through every save path as fields of their own; these four arrived with it and
 * would otherwise mean four more parameters on each of the five calls that
 * finish a card. Four positional parameters, two of them `Duration` and two
 * describing something else entirely, is exactly the place for two of them to be
 * swapped silently — the same reason `TodoAttributes` exists at all.
 *
 * A pomodoro rhythm is only a rhythm with both halves, so one half alone is read
 * as none.
 *
 * [deadlineAt] is the fifth, and it reuses a column that has been on `items`
 * since the first schema: `ItemType.DEADLINE` was meant to be a kind of card,
 * and nothing ever created one. A deadline is not a kind of card — it is
 * something a card may *have*, which is why it belongs here with the rest of the
 * extras and why no migration was needed to put it there.
 *
 * [quantity] is the sixth, the Mengen-Inkrement of step 24. Here rather than a
 * parameter of its own on every save path for the reason this class exists; it
 * is written through [withQuantity], which keeps the count a task has reached
 * across an edit that does not move its start.
 */
data class ItemExtras(
    val pomodoroWork: Duration? = null,
    val pomodoroPause: Duration? = null,
    val reminderLeadHours: Int? = null,
    val reminderMessage: String? = null,
    val deadlineAt: Instant? = null,
    val quantity: QuantitySetting? = null,
) {
    val hasPomodoro: Boolean get() = pomodoroWork != null && pomodoroPause != null

    val hasReminder: Boolean get() = reminderLeadHours != null

    val hasDeadline: Boolean get() = deadlineAt != null

    val hasQuantity: Boolean get() = quantity != null

    companion object {
        fun of(item: Item) = ItemExtras(
            pomodoroWork = item.pomodoroWork,
            pomodoroPause = item.pomodoroPause,
            reminderLeadHours = item.reminderLeadHours,
            reminderMessage = item.reminderMessage,
            deadlineAt = item.deadlineAt,
            quantity = item.quantitySetting,
        )
    }
}

/** The four columns, read off an item. */
val Item.extras: ItemExtras get() = ItemExtras.of(this)

/** The same ones, written back. Half a rhythm is stored as none at all. */
fun Item.withExtras(extras: ItemExtras): Item = copy(
    pomodoroWork = extras.pomodoroWork.takeIf { extras.hasPomodoro },
    pomodoroPause = extras.pomodoroPause.takeIf { extras.hasPomodoro },
    reminderLeadHours = extras.reminderLeadHours,
    reminderMessage = extras.reminderMessage?.takeIf { it.isNotBlank() },
    deadlineAt = extras.deadlineAt,
).withQuantity(extras.quantity)

/**
 * The pomodoro rhythm a definition hands to a block it produces.
 *
 * The same shape as the journeys and the break: the definition holds the default
 * and the block holds the truth, so a long press on the "now" box changes one
 * sitting and the form changes every sitting to come. One function rather than
 * two arguments at each of the four places a block is built, because forgetting
 * it at one of them would be a rhythm that silently never starts.
 */
fun PlannedBlock.stampedWith(item: Item): PlannedBlock = copy(
    pomodoroWork = item.pomodoroWork,
    pomodoroPause = item.pomodoroPause,
)
