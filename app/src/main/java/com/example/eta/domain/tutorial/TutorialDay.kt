package com.example.eta.domain.tutorial

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.setup.HousekeepingPlan
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WeeklySlot
import com.example.eta.domain.setup.WorkSchedule
import com.example.eta.domain.setup.recurringItems
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

const val TUTORIAL_AFTERNOON_ID = TUTORIAL_ITEM_ID_PREFIX + "work-afternoon"

/**
 * The example day's own tasks: breakfast behind, work running at
 * [TUTORIAL_MORNING], the cat next, a mail after it, and work again until five.
 *
 * All of them are **standing** tasks. The mail has to be one for the evening to
 * offer "Nachholen" — a dropped ToDo simply goes back to the Sammelliste and is
 * asked nothing — so it is a weekly mail, on whatever weekday the tutorial runs.
 * The others repeat daily, which is what puts them on tomorrow as well and
 * gives the day planner something to plan around.
 *
 * They stand **between** what an ordinary setup lays down — see [tutorialSetup]
 * — and the hours are chosen around it: breakfast after the morning routine,
 * the mail in the lunch hour, the afternoon's work done before sport.
 */
fun tutorialItems(today: DayOfWeek, now: Instant): List<Item> = listOf(
    Item.newRecurring(
        id = TUTORIAL_BREAKFAST_ID,
        name = "Frühstück",
        category = null,
        role = ItemRole.MEAL,
        recurrenceRule = RecurrenceRule.Daily,
        // After the setup's morning routine, which runs until a quarter to.
        startTime = LocalTime(7, 45),
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
        startTime = LocalTime(12, 30),
        estimatedDuration = 30.minutes,
        now = now,
    ),
    // The same name on purpose: it is the one job, before and after lunch.
    Item.newRecurring(
        id = TUTORIAL_AFTERNOON_ID,
        name = "Arbeiten",
        category = Category.FOKUS,
        role = ItemRole.WORK,
        recurrenceRule = RecurrenceRule.Daily,
        startTime = LocalTime(13, 0),
        estimatedDuration = 4.hours,
        now = now,
    ),
)

/**
 * Everything that stands in the practice week: the example tasks, and what the
 * practice setup lays down around them — morning routine, sport, housekeeping,
 * cooking, free time, mindfulness, bed preparation.
 */
fun tutorialSchedule(today: DayOfWeek, now: Instant, advanced: Boolean = false): List<Item> =
    tutorialItems(today, now) + tutorialSetup(now, advanced).recurringItems(now)

/**
 * The setup the practice database runs on: **the questionnaire's own defaults**,
 * as someone who accepts every page gets them — so the practice day looks like a
 * day of the app in use, with a morning, an evening and free time in it, and its
 * settlement reads like one.
 *
 * Two answers are changed so nothing collides with the example tasks. Work is
 * left out: the example's own "Arbeiten" is the working day, and the script needs
 * the cat to be what comes next rather than a lunch break. And the housekeeping
 * moves from Saturday morning, where the daily work now stands, to the late
 * afternoon. `TutorialTest` pins that no two tasks overlap on any weekday.
 *
 * The points are on, as they are for a newcomer; the other Advanced Features
 * only where the tutorial is about one ([advanced]).
 *
 * Never the user's own answers — the tutorial reads nothing of theirs either.
 */
fun tutorialSetup(now: Instant, advanced: Boolean = false): UserSetup = UserSetup.draft(now)
    .copy(
        work = WorkSchedule.None,
        housekeeping = HousekeepingPlan.Weekly(
            WeeklySlot(DayOfWeek.SATURDAY, LocalTime(17, 15), 1.hours),
        ),
        // The hour the simulated day jumps to: a quiet countdown at the foot of
        // the dashboard all morning, and owed — at the top — the moment the
        // tutorial says it is evening.
        dailyPlanningTime = TUTORIAL_EVENING,
        pointsSystem = true,
        growthTasks = advanced,
        contracts = advanced,
        rewards = advanced,
        completedAt = now,
        updatedAt = now,
    )
