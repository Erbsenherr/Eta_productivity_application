package com.example.eta.domain.tutorial

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.setup.SetupPart
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.skipping
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** Every example task carries this prefix, which is how the user's own are told apart. */
const val TUTORIAL_ITEM_ID_PREFIX = "tutorial:"

const val TUTORIAL_BREAKFAST_ID = TUTORIAL_ITEM_ID_PREFIX + "breakfast"
const val TUTORIAL_WORK_ID = TUTORIAL_ITEM_ID_PREFIX + "work"
const val TUTORIAL_CAT_ID = TUTORIAL_ITEM_ID_PREFIX + "cat"
const val TUTORIAL_MAIL_ID = TUTORIAL_ITEM_ID_PREFIX + "mail"

/** The three the evening ticks off; the mail is the one that is not. */
val TUTORIAL_DONE_IDS = setOf(TUTORIAL_BREAKFAST_ID, TUTORIAL_WORK_ID, TUTORIAL_CAT_ID)

/**
 * The example day: breakfast behind, work running at [TUTORIAL_MORNING], the cat
 * next, a mail in the afternoon.
 *
 * All four are **standing** tasks. The mail has to be one for the evening to
 * offer "Nachholen" — a dropped ToDo simply goes back to the Sammelliste and is
 * asked nothing — so it is a weekly mail, on whatever weekday the tutorial runs.
 * The other three repeat daily, which is what puts them on tomorrow as well and
 * gives the day planner something to plan around.
 */
fun tutorialItems(today: DayOfWeek, now: Instant): List<Item> = listOf(
    Item.newRecurring(
        id = TUTORIAL_BREAKFAST_ID,
        name = "Frühstück",
        category = null,
        role = ItemRole.MEAL,
        recurrenceRule = RecurrenceRule.Daily,
        startTime = LocalTime(7, 30),
        estimatedDuration = 30.minutes,
        now = now,
    ),
    Item.newRecurring(
        id = TUTORIAL_WORK_ID,
        name = "Arbeiten",
        category = Category.FOKUS,
        role = ItemRole.WORK,
        recurrenceRule = RecurrenceRule.Daily,
        startTime = LocalTime(9, 0),
        estimatedDuration = 3.hours,
        now = now,
    ),
    Item.newRecurring(
        id = TUTORIAL_CAT_ID,
        name = "Katze füttern",
        category = Category.NEBENBEI,
        recurrenceRule = RecurrenceRule.Daily,
        startTime = LocalTime(12, 0),
        estimatedDuration = 15.minutes,
        now = now,
    ),
    Item.newRecurring(
        id = TUTORIAL_MAIL_ID,
        name = "Mail versenden",
        category = Category.FOKUS,
        recurrenceRule = RecurrenceRule.Weekly(today),
        startTime = LocalTime(14, 0),
        estimatedDuration = 30.minutes,
        now = now,
    ),
)

/**
 * The setup the practice database runs on: the questionnaire's defaults with
 * every optional page skipped, so the week is as good as empty, and every
 * Advanced Feature off, which is what a newcomer's own app looks like.
 *
 * Never the user's own answers — the tutorial reads nothing of theirs either.
 */
fun tutorialSetup(now: Instant): UserSetup = UserSetup.draft(now)
    .skipping(SetupPart.entries.toSet())
    .copy(
        // The hour the simulated day jumps to: a quiet countdown at the foot of
        // the dashboard all morning, and owed — at the top — the moment the
        // tutorial says it is evening.
        dailyPlanningTime = TUTORIAL_EVENING,
        pointsSystem = false,
        growthTasks = false,
        contracts = false,
        rewards = false,
        completedAt = now,
        updatedAt = now,
    )
