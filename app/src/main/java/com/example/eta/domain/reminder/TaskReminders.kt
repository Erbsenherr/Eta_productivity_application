package com.example.eta.domain.reminder

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.Item
import com.example.eta.domain.planning.startsAt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** What the checkbox suggests, and the range the stepper allows. */
const val DEFAULT_REMINDER_LEAD_HOURS = 1
const val MIN_REMINDER_LEAD_HOURS = 1
const val MAX_REMINDER_LEAD_HOURS = 24

/** The name the reminder goes by, which the user asked for by name. */
fun taskReminderTitle(item: Item): String = "Erinnerung: ${item.name}"

/**
 * What the reminder says: its name, and the message if one was written.
 *
 * One string rather than two columns on `reminders`, because a reminder set by
 * hand has exactly one line of text and these have to be the same kind of thing
 * — they are listed, edited and deleted in the same tab.
 */
fun taskReminderText(item: Item): String {
    val message = item.reminderMessage?.takeIf { it.isNotBlank() }
    return if (message == null) taskReminderTitle(item) else "${taskReminderTitle(item)} — $message"
}

/** One reminder a task owes, as it should stand in the Erinnerungen tab. */
data class TaskReminderPlan(
    val itemId: String,
    val blockId: String,
    val at: Instant,
    val text: String,
)

/**
 * The reminders the plan asks for: **one per task**, aimed at its next occurrence.
 *
 * One rather than one per materialized occurrence, as the user chose. The
 * schedule is laid down three weeks ahead, so a daily task would otherwise put
 * twenty-one rows in a tab whose whole job is to be read at a glance — and the
 * twenty-first says nothing the first does not.
 *
 * Counted from the **container** start, so a task with a journey in front of it
 * is announced an hour before setting off rather than an hour before arriving.
 * Only open occurrences count: one already ticked off or called off has been
 * dealt with, and reminding the user of it would be telling them something they
 * told the app.
 */
fun taskReminderPlans(
    blocks: List<BlockWithItem>,
    now: Instant,
    timeZone: TimeZone,
): List<TaskReminderPlan> = blocks
    .filter { it.block.isOpen && it.item.reminderLeadHours != null }
    .mapNotNull { entry ->
        val lead = entry.item.reminderLeadHours ?: return@mapNotNull null
        val at = entry.block.startsAt(timeZone) -
            (entry.block.travelBefore ?: kotlin.time.Duration.ZERO) -
            lead.hours
        // A reminder whose moment has already gone by is not owed: the occurrence
        // it belonged to is nearly here, and ringing late would only confuse.
        if (at <= now) return@mapNotNull null
        TaskReminderPlan(
            itemId = entry.item.id,
            blockId = entry.block.id,
            at = at,
            text = taskReminderText(entry.item),
        )
    }
    .groupBy { it.itemId }
    .values
    .mapNotNull { plans -> plans.minByOrNull { it.at } }
    .sortedBy { it.at }
