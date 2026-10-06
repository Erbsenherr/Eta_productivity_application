package com.example.eta.domain.setup

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.MINUTES_PER_DAY
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Marks the recurring definitions the questionnaire owns, and makes their ids
 * deterministic: answering the questionnaire again updates exactly those rows
 * instead of laying a second schedule on top of the first.
 */
const val SETUP_ITEM_ID_PREFIX = "setup:"

private fun setupId(key: String) = SETUP_ITEM_ID_PREFIX + key

/**
 * The two tasks the settings tab still owns: winding down and the morning.
 *
 * Everything else the questionnaire lays down is created once and from then on is
 * an ordinary standing task, edited on the Listen tab. These two stay with the
 * settings because they hang off the night — bed preparation ends at the sleep
 * time and the morning starts at the wake time — and the night is configuration,
 * not a task. Saving the settings regenerates exactly these, and nothing else —
 * see [isOwnedBySettings].
 */
private val SETTINGS_OWNED_KEYS = listOf("bedprep", "morning")

/**
 * Whether [id] is one of them. With every night alike each is one daily
 * definition (`setup:bedprep`); with a weekend of its own there is one per night,
 * named after the day that night ends on (`setup:bedprep-saturday`).
 */
fun isOwnedBySettings(id: String): Boolean = SETTINGS_OWNED_KEYS.any { key ->
    id == setupId(key) || id.startsWith(setupId("$key-"))
}

/**
 * The recurring definitions that follow from the answers.
 *
 * Category is what decides whether a block pays points, so it is assigned by
 * meaning rather than uniformly: the framework of the day — winding down, the
 * morning, the lunch break, free time — occupies time without a category and
 * therefore yields nothing, while the things that are an achievement carry one.
 * "Arbeit / Uni" counts as Fokus for now; the user can correct any of it once
 * recurring tasks are editable from the planner.
 *
 * Sleep produces no item at all: the planner shades those hours from the setup.
 */
fun UserSetup.recurringItems(now: Instant): List<Item> {
    val items = mutableListOf<Item>()

    fun daily(
        key: String,
        name: String,
        category: Category?,
        start: LocalTime,
        duration: Duration,
        role: ItemRole?,
    ) {
        if (duration.inWholeMinutes <= 0) return
        items += Item.newRecurring(
            id = setupId(key),
            name = name,
            category = category,
            recurrenceRule = RecurrenceRule.Daily,
            startTime = start,
            estimatedDuration = duration,
            now = now,
            role = role,
        )
    }

    /**
     * One definition per chosen weekday: a slot on two days is two recurrences,
     * and the id carries the day so re-answering keeps updating the same rows.
     */
    fun weekly(
        key: String,
        name: String,
        category: Category?,
        slot: WeeklySlot,
        role: ItemRole?,
        rule: (DayOfWeek) -> RecurrenceRule = { RecurrenceRule.Weekly(it) },
    ) {
        if (slot.duration.inWholeMinutes <= 0) return
        slot.orderedWeekdays.forEach { weekday ->
            items += Item.newRecurring(
                id = setupId("$key-${weekday.name.lowercase()}"),
                name = name,
                category = category,
                recurrenceRule = rule(weekday),
                startTime = slot.start,
                estimatedDuration = slot.duration,
                now = now,
                role = role,
            )
        }
    }

    if (weekendNight == null) {
        daily("bedprep", SetupLabels.BED_PREP, null, bedPrepTime, bedPrepDuration(), ItemRole.BED_PREP)
        daily("morning", SetupLabels.MORNING, null, wakeTime, morningDuration, ItemRole.MORNING)
    } else {
        // One pair per night. The id carries the day the night *ends* on, not the
        // day the task falls on: winding down after midnight lands on the wake
        // day itself, and two nights could otherwise claim the same weekday's id.
        fun nightly(key: String, name: String, wakeDay: DayOfWeek, onDay: DayOfWeek, start: LocalTime, duration: Duration, role: ItemRole) {
            if (duration.inWholeMinutes <= 0) return
            items += Item.newRecurring(
                id = setupId("$key-${wakeDay.name.lowercase()}"),
                name = name,
                category = null,
                recurrenceRule = RecurrenceRule.Weekly(onDay),
                startTime = start,
                estimatedDuration = duration,
                now = now,
                role = role,
            )
        }
        WEEK.forEach { wakeDay ->
            val night = nightEndingOn(wakeDay)
            nightly(
                key = "bedprep",
                name = SetupLabels.BED_PREP,
                wakeDay = wakeDay,
                onDay = wakeDay.shifted(Math.floorDiv(night.bedPrepOffset(), MINUTES_PER_DAY)),
                start = night.bedPrep,
                duration = night.bedPrepDuration(),
                role = ItemRole.BED_PREP,
            )
            nightly("morning", SetupLabels.MORNING, wakeDay, wakeDay, night.wake, morningDuration, ItemRole.MORNING)
        }
    }
    daily("freetime", SetupLabels.FREE_TIME, null, freeTime.start, freeTime.duration, ItemRole.FREE_TIME)

    when (val plan = meals) {
        is MealPlan.MealPrep -> weekly(
            key = "mealprep",
            name = SetupLabels.MEAL_PREP,
            category = Category.NEBENBEI,
            slot = plan.slot,
            role = ItemRole.MEAL,
        )

        is MealPlan.DailyCooking -> plan.slots.forEachIndexed { index, slot ->
            daily(
                key = "cooking-$index",
                name = SetupLabels.COOKING,
                category = Category.NEBENBEI,
                start = slot.start,
                duration = slot.duration,
                role = ItemRole.MEAL,
            )
        }
    }

    housekeeping?.let {
        weekly(
            key = "housekeeping",
            name = SetupLabels.HOUSEKEEPING,
            category = Category.NEBENBEI,
            slot = it.slot,
            role = ItemRole.HOUSEKEEPING,
            rule = it::rulesFor,
        )
    }

    sport?.let {
        weekly(
            key = "sport",
            name = SetupLabels.SPORT,
            category = Category.FOKUS,
            slot = it,
            role = ItemRole.SPORT,
        )
    }

    when (val plan = mindfulness) {
        is MindfulnessPlan.EveryDay -> daily(
            key = "mindfulness",
            name = SetupLabels.MINDFULNESS,
            category = Category.ACHTSAM,
            start = plan.slot.start,
            duration = plan.slot.duration,
            role = ItemRole.MINDFULNESS,
        )

        is MindfulnessPlan.Weekly -> weekly(
            key = "mindfulness",
            name = SetupLabels.MINDFULNESS,
            category = Category.ACHTSAM,
            slot = plan.slot,
            role = ItemRole.MINDFULNESS,
        )

        null -> Unit
    }

    // One definition per weekday and piece: a block with a break becomes three
    // definitions — work, break, work — and the uniform answer is just the case
    // where all five workdays happen to carry the same hours.
    WEEK.forEach { weekday ->
        work.segmentsOn(weekday).forEachIndexed { index, segment ->
            val isPause = segment.kind == WorkSegmentKind.PAUSE
            weekly(
                key = "${if (isPause) "pause" else "work"}-$index",
                name = if (isPause) SetupLabels.PAUSE else SetupLabels.WORK,
                // The break is part of the frame of the day and pays nothing.
                category = if (isPause) null else Category.FOKUS,
                slot = WeeklySlot(weekday, segment.span.start, segment.span.duration),
                role = if (isPause) ItemRole.BREAK else ItemRole.WORK,
            )
        }
    }

    return items
}
