package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Priority
import com.example.eta.domain.recurrence.expandRecurring
import com.example.eta.domain.tutorial.QUICKSTART_STEPS
import com.example.eta.domain.tutorial.TUTORIAL_EXTRAS_NOTE
import com.example.eta.domain.tutorial.TutorialId
import com.example.eta.domain.tutorial.StepKind
import com.example.eta.domain.tutorial.TUTORIAL_EVENING
import com.example.eta.domain.tutorial.TUTORIAL_MAIL_ID
import com.example.eta.domain.tutorial.TUTORIAL_MORNING
import com.example.eta.domain.tutorial.TUTORIAL_WORK_ID
import com.example.eta.domain.tutorial.TutorialClock
import com.example.eta.domain.tutorial.TutorialFacts
import com.example.eta.domain.tutorial.TutorialGate
import com.example.eta.domain.tutorial.TutorialSpot
import com.example.eta.domain.tutorial.TutorialSignal
import com.example.eta.domain.tutorial.TutorialStage
import com.example.eta.domain.tutorial.exitTarget
import com.example.eta.domain.tutorial.remembering
import com.example.eta.domain.tutorial.tutorialFacts
import com.example.eta.domain.tutorial.TUTORIAL_BREAKFAST_ID
import com.example.eta.domain.tutorial.TUTORIAL_CAT_ID
import com.example.eta.domain.tutorial.tutorialItems
import com.example.eta.domain.tutorial.tutorialSchedule
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.reevaluation.settleDay
import com.example.eta.domain.setup.conflicts
import com.example.eta.domain.planning.sleepStretches
import kotlinx.datetime.DayOfWeek
import com.example.eta.domain.tutorial.tutorialSetup
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.nowAndNext
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.recurringItems
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialTest {

    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-10-09T23:58:00Z")
    private val today = LocalDate(2026, 10, 9)

    /** Three of the example's own tasks — not the whole day, which is the point of some tests. */
    private val TUTORIAL_DONE_IDS = setOf(TUTORIAL_BREAKFAST_ID, TUTORIAL_WORK_ID, TUTORIAL_CAT_ID)

    private class FixedClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    /** The example day as the seed lays it down: one block per task, today. */
    private fun exampleDay(): List<BlockWithItem> {
        val items = tutorialSchedule(today.dayOfWeek, now)
        val byId = items.associateBy { it.id }
        return expandRecurring(items, today, today, emptySet(), now)
            .map { BlockWithItem(it, byId.getValue(it.itemId)) }
    }

    /** Everything on the day but the mail: what the evening is asked to tick off. */
    private val List<BlockWithItem>.othersIds: Set<String>
        get() = map { it.item.id }.filterTo(mutableSetOf()) { it != TUTORIAL_MAIL_ID }

    private fun List<BlockWithItem>.completing(ids: Set<String>) = map { entry ->
        if (entry.item.id in ids) entry.copy(block = entry.block.copy(completedAt = now)) else entry
    }

    private fun List<BlockWithItem>.changing(id: String, change: (PlannedBlock) -> PlannedBlock) =
        map { entry -> if (entry.item.id == id) entry.copy(block = change(entry.block)) else entry }

    // --- the clock ----------------------------------------------------------

    @Test
    fun `the simulated day begins at half past ten whatever the real hour`() {
        // Two minutes to midnight: the hour at which a day laid around the real
        // clock would have had no room left for anything.
        val clock = TutorialClock(real = FixedClock(now), timeZone = zone)

        val simulated = clock.now().toLocalDateTime(zone)
        assertEquals(today, simulated.date)
        assertEquals(TUTORIAL_MORNING, simulated.time)
    }

    @Test
    fun `the simulated day runs on and does not roll over with the real one`() {
        val real = FixedClock(now)
        val clock = TutorialClock(real = real, timeZone = zone)

        real.instant = now + 10.minutes

        val simulated = clock.now().toLocalDateTime(zone)
        assertEquals(today, simulated.date)
        assertEquals(TUTORIAL_MORNING.minuteOfDay() + 10, simulated.time.minuteOfDay())
        assertEquals(today, clock.today)
    }

    @Test
    fun `jumping to the evening keeps the date`() {
        val clock = TutorialClock(real = FixedClock(now), timeZone = zone)

        clock.jumpTo(TUTORIAL_EVENING)

        val simulated = clock.now().toLocalDateTime(zone)
        assertEquals(today, simulated.date)
        assertEquals(TUTORIAL_EVENING, simulated.time)
    }

    // --- the example day ----------------------------------------------------

    @Test
    fun `the example tasks stand on today among what a setup lays down`() {
        val day = exampleDay()
        val names = day.map { it.item.name }

        assertTrue(names.containsAll(listOf("Frühstück", "Arbeiten", "Katze füttern", "Mail versenden")))
        // The frame an ordinary setup gives a day, around them.
        assertTrue(day.any { it.item.role == ItemRole.MORNING })
        assertTrue(day.any { it.item.role == ItemRole.FREE_TIME })
        assertTrue(day.any { it.item.role == ItemRole.BED_PREP })
        assertTrue(day.size > 5)
        assertTrue(day.all { it.item.isConcretized })
        assertTrue(day.all { it.block.origin == BlockOrigin.RECURRING })
    }

    @Test
    fun `nothing in the practice week overlaps, on whichever weekday it is run`() {
        DayOfWeek.entries.forEach { runOn ->
            val items = tutorialSchedule(runOn, now)
            val byId = items.associateBy { it.id }
            // A whole week from the day it is run on: every weekday's own mix.
            val start = (0..6).map { today.plus(DatePeriod(days = it)) }.first { it.dayOfWeek == runOn }
            (0..6).map { start.plus(DatePeriod(days = it)) }.forEach { date ->
                val spans = expandRecurring(items, date, date, emptySet(), now)
                    .map { block ->
                        val from = block.start.minuteOfDay()
                        Triple(byId.getValue(block.itemId).name, from, from + block.plannedDuration.inWholeMinutes.toInt())
                    }
                    .sortedBy { it.second }
                spans.zipWithNext().forEach { (a, b) ->
                    assertTrue(
                        "${a.first} runs into ${b.first} on ${date.dayOfWeek} (run on $runOn)",
                        a.third <= b.second,
                    )
                }
                assertTrue(spans.all { it.third <= 24 * 60 })
            }
        }
    }

    @Test
    fun `the practice setup collides with nothing of its own either`() {
        assertEquals(emptyList<Any>(), tutorialSetup(now).conflicts())
    }

    @Test
    fun `the daily tasks stand on tomorrow as well and the weekly mail does not`() {
        val items = tutorialSchedule(today.dayOfWeek, now)
        val tomorrow = today.plus(DatePeriod(days = 1))

        val ids = expandRecurring(items, tomorrow, tomorrow, emptySet(), now).map { it.itemId }.toSet()

        assertTrue(ids.containsAll(setOf(TUTORIAL_BREAKFAST_ID, TUTORIAL_WORK_ID, TUTORIAL_CAT_ID)))
        assertFalse(TUTORIAL_MAIL_ID in ids)
    }

    @Test
    fun `the example day settles in the user's favour`() {
        // Played as the tutorial asks: everything ticked, the mail called off
        // and excused. A first settlement that came out negative would teach
        // that planning costs.
        val setup = tutorialSetup(now)
        val day = exampleDay().let { it.completing(it.othersIds) }
            .changing(TUTORIAL_MAIL_ID) { it.copy(discardedAt = now, forceMajeure = "Server") }
        val settlement = settleDay(
            blocks = day,
            verdicts = emptyList(),
            sleepMinutes = setup.sleepStretches(today.dayOfWeek).sumOf { it.last - it.first + 1 },
            freeTimeAllowanceMinutes = day.filter { it.item.role == ItemRole.FREE_TIME }
                .sumOf { it.block.plannedDuration.inWholeMinutes.toInt() },
            timeZone = zone,
            unplannedPenaltyPerHour = setup.unplannedRate,
        )

        assertTrue("harvest ${settlement.harvest}", settlement.harvest >= 6.0)
        assertTrue("total ${settlement.total}", settlement.total > 0.0)
    }

    @Test
    fun `at the tutorial's hour work is running and the cat is next`() {
        val state = nowAndNext(exampleDay(), TUTORIAL_MORNING.minuteOfDay())

        assertEquals(TUTORIAL_WORK_ID, state.current?.entry?.item?.id)
        assertEquals("Katze füttern", state.next?.entry?.item?.name)
    }

    @Test
    fun `the practice setup shows what a newcomer sees`() {
        val setup = tutorialSetup(now)

        // The points, and nothing else — exactly a fresh setup's own answer.
        assertTrue(setup.pointsSystem)
        assertFalse(setup.growthTasks || setup.contracts || setup.rewards)
        val fresh = UserSetup.draft(now)
        assertTrue(fresh.pointsSystem)
        assertFalse(fresh.growthTasks || fresh.contracts || fresh.rewards)
    }

    @Test
    fun `a tutorial about a feature has the features switched on`() {
        val setup = tutorialSetup(now, advanced = true)

        assertTrue(setup.pointsSystem && setup.growthTasks && setup.contracts && setup.rewards)
        assertEquals(
            setOf(TutorialId.GROWTH, TutorialId.CONTRACTS, TutorialId.REWARDS),
            TutorialId.entries.filter { it.advanced }.toSet(),
        )
    }

    // --- the facts ----------------------------------------------------------

    @Test
    fun `a catch-up in the Sammelliste is not the note the tutorial asked for`() {
        val mail = tutorialItems(today.dayOfWeek, now).first { it.id == TUTORIAL_MAIL_ID }
        val makeUp = Item.newMakeUpTodo(mail, now)

        val facts = tutorialFacts(listOf(makeUp), emptyList(), exampleDay(), emptyList(), false)

        assertFalse(facts.todoNoted)
        assertNull(facts.todoName)
        assertEquals(0, facts.openTodoNotes)
    }

    @Test
    fun `the two notes are told apart and counted as open until filled in`() {
        val todo = Item.newQuickTodo("Katzenstreu kaufen", now)
        val recurring = Item.newQuickRecurring("Regelmäßig Sport", now)

        val facts = tutorialFacts(listOf(todo, recurring), emptyList(), exampleDay(), emptyList(), false)

        assertTrue(facts.todoNoted && facts.recurringNoted)
        assertEquals("»Katzenstreu kaufen«", facts.todoLabel)
        assertEquals("»Regelmäßig Sport«", facts.recurringLabel)
        assertEquals(1, facts.openTodoNotes)
        assertEquals(1, facts.openRecurringNotes)
    }

    @Test
    fun `a note that has left the Sammelliste is still remembered`() {
        val recurring = Item.newQuickRecurring("Regelmäßig Sport", now)
        val before = tutorialFacts(listOf(recurring), emptyList(), exampleDay(), emptyList(), false)

        // Filled in, it moves to the standing schedule and the list is empty.
        val after = tutorialFacts(emptyList(), emptyList(), exampleDay(), emptyList(), false)
            .remembering(before)

        assertTrue(after.recurringNoted)
        assertEquals("Regelmäßig Sport", after.recurringName)
        assertEquals(0, after.openRecurringNotes)
    }

    @Test
    fun `the mail is read off its own block`() {
        val day = exampleDay().let { it.completing(it.othersIds) }
            .changing(TUTORIAL_MAIL_ID) { it.copy(discardedAt = now, makeUpItemId = "x") }

        val facts = tutorialFacts(emptyList(), emptyList(), day, emptyList(), false)

        assertTrue(facts.completedToday.containsAll(TUTORIAL_DONE_IDS))
        assertEquals(0, facts.othersOpen)
        assertTrue(facts.mailDiscarded && facts.mailMadeUp && facts.mailAnswered)
        assertFalse(facts.mailCompleted)
    }

    @Test
    fun `only a card placed by hand counts as planned into tomorrow`() {
        val standing = exampleDay()
        val todo = Item.newTodo("Katzenstreu kaufen", Category.NEBENBEI, Priority.WANT, null, 1.hours, now)
        val dragged = BlockWithItem(
            PlannedBlock(
                itemId = todo.id,
                date = today,
                start = TUTORIAL_EVENING,
                plannedDuration = 1.hours,
                origin = BlockOrigin.DRAGGED,
                createdAt = now,
                updatedAt = now,
            ),
            todo,
        )

        assertFalse(tutorialFacts(emptyList(), emptyList(), emptyList(), standing, false).plannedTomorrow)
        assertTrue(tutorialFacts(emptyList(), emptyList(), emptyList(), standing + dragged, false).plannedTomorrow)
    }

    // --- the script ---------------------------------------------------------

    @Test
    fun `the stages come in order and each is one stretch`() {
        val stages = QUICKSTART_STEPS.map { it.stage }

        assertEquals(TutorialStage.entries.take(5), stages.distinct())
        assertEquals(stages.sortedBy { it.ordinal }, stages)
    }

    @Test
    fun `the script starts and ends on a step that can simply be read`() {
        assertEquals(StepKind.TEXT, QUICKSTART_STEPS.first().kind)
        assertEquals(StepKind.TEXT, QUICKSTART_STEPS.last().kind)
    }

    @Test
    fun `the evening falls on the dashboard and its own button leads out of it`() {
        val evening = QUICKSTART_STEPS.filter { it.evening }

        // One step, the last of the dashboard: the user is told it is time and
        // presses "Tag abschließen" themselves.
        assertEquals(1, evening.size)
        assertEquals(QUICKSTART_STEPS.last { it.stage == TutorialStage.DASHBOARD }, evening.single())
        assertEquals(StepKind.FOLLOW, evening.single().kind)
        assertEquals(TutorialSpot.PHASE, evening.single().spot)

        val index = QUICKSTART_STEPS.indexOf(evening.single())
        assertEquals(
            QUICKSTART_STEPS.indexOfFirst { it.stage == TutorialStage.REEVALUATION },
            exitTarget(QUICKSTART_STEPS, index, TutorialStage.DASHBOARD, TutorialFacts()),
        )
    }

    @Test
    fun `the planning hour of the practice setup is the hour the day jumps to`() {
        assertEquals(TUTORIAL_EVENING, tutorialSetup(now).dailyPlanningTime)
    }

    @Test
    fun `the mail ticked off holds the first evening step shut`() {
        val step = QUICKSTART_STEPS.first { it.title == "Tagesabschluss" }
        val day = exampleDay()
        val others = day.completing(day.othersIds)
        val all = day.completing(day.othersIds + TUTORIAL_MAIL_ID)
        // One of the setup's own tasks left open is still "not everything".
        val nearly = day.completing(TUTORIAL_DONE_IDS)

        assertTrue(step.ready(tutorialFacts(emptyList(), emptyList(), others, emptyList(), false)))
        assertFalse(step.ready(tutorialFacts(emptyList(), emptyList(), nearly, emptyList(), false)))
        val facts = tutorialFacts(emptyList(), emptyList(), all, emptyList(), false)
        assertFalse(step.ready(facts))
        assertEquals(2, step.checks(facts).size)
    }

    @Test
    fun `a screen's own buttons are opened by the step that asks for them and by no other`() {
        fun opening(gate: String) = QUICKSTART_STEPS.filter { gate in it.allow }.map { it.title }

        assertEquals(listOf("Was nicht geklappt hat"), opening(TutorialGate.REEVALUATION_DISCARD + TUTORIAL_MAIL_ID))
        assertEquals(listOf("Passt das noch?"), opening(TutorialGate.REEVALUATION_SETTLE))
        // "Übernehmen" is a step of its own for each card, with the frame on it.
        assertEquals(
            listOf(TutorialSpot.TODO_SAVE),
            QUICKSTART_STEPS.filter { TutorialGate.CONCRETIZE_TODO in it.allow }.map { it.spot },
        )
        assertEquals(
            listOf(TutorialSpot.RECURRING_SAVE),
            QUICKSTART_STEPS.filter { TutorialGate.CONCRETIZE_RECURRING in it.allow }.map { it.spot },
        )
        assertEquals(listOf(QUICKSTART_STEPS.last().title), opening(TutorialGate.PLANNER_CONFIRM))
        // The way to the day planning stays shut; one step says it comes later.
        assertTrue(opening(TutorialGate.CONCRETIZE_DONE).isEmpty())
        assertEquals(listOf("Geschafft"), opening(TutorialGate.CONCRETIZE_LATER_NOTE))
        assertEquals(listOf("Die Woche steht"), opening(TutorialGate.WEEK_FINISH))
        assertTrue(opening(TutorialGate.CONCRETIZE_DELETE).isEmpty())
        assertTrue(opening(TutorialGate.REEVALUATION_DISMISS_MAKE_UP).isEmpty())
        // The page of the Tagesabschluss turns only once every block is answered.
        val firstTurn = QUICKSTART_STEPS.indexOfFirst { TutorialGate.REEVALUATION_NEXT in it.allow }
        assertTrue(firstTurn > QUICKSTART_STEPS.indexOfFirst { it.title == "Höhere Gewalt" })
    }

    @Test
    fun `höhere Gewalt is asked for after the mail was called off`() {
        val step = QUICKSTART_STEPS.first { it.title == "Höhere Gewalt" }
        val called = exampleDay().changing(TUTORIAL_MAIL_ID) { it.copy(discardedAt = now, makeUpItemId = "x") }
        val excused = called.changing(TUTORIAL_MAIL_ID) { it.copy(forceMajeure = "Der Mailserver war ausgefallen.") }

        assertFalse(step.ready(tutorialFacts(emptyList(), emptyList(), called, emptyList(), false)))
        assertTrue(step.ready(tutorialFacts(emptyList(), emptyList(), excused, emptyList(), false)))
    }

    @Test
    fun `no task is done before anything has happened`() {
        val untouched = tutorialFacts(emptyList(), emptyList(), exampleDay(), emptyList(), false)

        QUICKSTART_STEPS.filter { it.kind == StepKind.TASK }.forEach { step ->
            assertFalse("»${step.title}« is ready on an untouched day", step.ready(untouched))
            assertTrue("»${step.title}« shows nothing to do", step.checks(untouched).isNotEmpty())
        }
    }

    @Test
    fun `a day played through as asked leaves no task open`() {
        val todo = Item.newTodo("Katzenstreu kaufen", Category.NEBENBEI, Priority.WANT, null, 1.hours, now)
        val day = exampleDay().let { it.completing(it.othersIds) }
            .changing(TUTORIAL_MAIL_ID) {
                it.copy(discardedAt = now, makeUpItemId = "x", forceMajeure = "Server")
            }
        val dragged = BlockWithItem(
            PlannedBlock(
                itemId = todo.id,
                date = today.plus(DatePeriod(days = 1)),
                start = TUTORIAL_EVENING,
                plannedDuration = 1.hours,
                origin = BlockOrigin.DRAGGED,
                createdAt = now,
                updatedAt = now,
            ),
            todo,
        )
        val played = tutorialFacts(emptyList(), listOf(todo), day, listOf(dragged), true)
            .copy(
                todoNoted = true,
                recurringNoted = true,
                seen = setOf(
                    "${TutorialSignal.NOW_PAGE}=1",
                    "${TutorialSignal.PLANNER_REVOLVER}=SPEND",
                ),
            )

        QUICKSTART_STEPS.filter { it.kind == StepKind.TASK }.forEach { step ->
            assertTrue("»${step.title}« is still open", step.ready(played))
        }
    }

    @Test
    fun `the mail step lets go when the question can no longer be answered`() {
        val step = QUICKSTART_STEPS.first { it.title == "Was nicht geklappt hat" }
        val discarded = exampleDay().changing(TUTORIAL_MAIL_ID) { it.copy(discardedAt = now) }
        val facts = tutorialFacts(emptyList(), emptyList(), discarded, emptyList(), false)

        // "Fällt aus" tapped, the question still standing: wait for the answer.
        assertFalse(step.ready(facts.copy(signals = mapOf(TutorialSignal.OPEN_MAKE_UP_OFFERS to "1"))))
        // The question gone without an answer: it will not be asked again.
        assertTrue(step.ready(facts.copy(signals = mapOf(TutorialSignal.OPEN_MAKE_UP_OFFERS to "0"))))
        // Ticked off instead: not what the day was, and the tick can be taken back.
        val done = exampleDay().completing(setOf(TUTORIAL_MAIL_ID))
        assertFalse(step.ready(tutorialFacts(emptyList(), emptyList(), done, emptyList(), false)))
    }

    // --- the screens' own ways out ------------------------------------------

    @Test
    fun `a way out is refused while the stage still asks for something`() {
        val first = QUICKSTART_STEPS.indexOfFirst { it.stage == TutorialStage.WEEK }

        assertNull(exitTarget(QUICKSTART_STEPS, first, TutorialStage.WEEK, TutorialFacts()))
    }

    @Test
    fun `a way out leads to the next stage once nothing is left undone`() {
        val first = QUICKSTART_STEPS.indexOfFirst { it.stage == TutorialStage.WEEK }
        val planner = QUICKSTART_STEPS.indexOfFirst { it.stage == TutorialStage.PLANNER }

        assertEquals(planner, exitTarget(QUICKSTART_STEPS, first, TutorialStage.WEEK, TutorialFacts(weekGoals = 1)))
    }

    @Test
    fun `a way out of another screen than the one in front is nothing`() {
        assertNull(exitTarget(QUICKSTART_STEPS, 0, TutorialStage.PLANNER, TutorialFacts(plannedTomorrow = true)))
    }

    @Test
    fun `leaving the planner shows the closing words first and ends the tutorial after`() {
        val first = QUICKSTART_STEPS.indexOfFirst { it.stage == TutorialStage.PLANNER }
        val done = TutorialFacts(
            plannedTomorrow = true,
            seen = setOf("${TutorialSignal.PLANNER_REVOLVER}=SPEND"),
        )

        assertNull(exitTarget(QUICKSTART_STEPS, first, TutorialStage.PLANNER, TutorialFacts()))
        // The card placed, the second revolver not yet looked at: not yet.
        assertNull(exitTarget(QUICKSTART_STEPS, first, TutorialStage.PLANNER, TutorialFacts(plannedTomorrow = true)))
        assertEquals(QUICKSTART_STEPS.lastIndex, exitTarget(QUICKSTART_STEPS, first, TutorialStage.PLANNER, done))
        assertEquals(QUICKSTART_STEPS.size, exitTarget(QUICKSTART_STEPS, QUICKSTART_STEPS.lastIndex, TutorialStage.PLANNER, done))
    }

    // --- the points in the Quickstart ---------------------------------------

    @Test
    fun `the Quickstart shows the account, the settlement and the second revolver`() {
        val titles = QUICKSTART_STEPS.map { it.title }

        assertEquals(TutorialSpot.POINTS, QUICKSTART_STEPS.first { it.title == "Dein Punktekonto" }.spot)
        assertTrue(QUICKSTART_STEPS.first { it.title == "Dein Punktekonto" }.text(TutorialFacts()).contains("Advanced Features"))
        // The settlement is the page between the tasks and the journal.
        assertTrue(titles.indexOf("Die Abrechnung") in (titles.indexOf("Hervorragend!") + 1) until titles.indexOf("Kurz nachdenken"))
        // Why empty time costs, straight after the page that shows it costing.
        assertEquals(titles.indexOf("Die Abrechnung") + 1, titles.indexOf("Warum leere Zeit Punkte kostet"))
        // And the third revolver after the second.
        assertEquals(titles.indexOf("Punkte verdienen und ausgeben") + 1, titles.indexOf("Der dritte Revolver: Pause"))
        assertTrue(titles.indexOf("Der zweite Revolver") > titles.indexOf("Der Tagesplaner"))
        // Reading a block too short for its name comes right after placing one.
        assertEquals(titles.indexOf("Der Tagesplaner") + 1, titles.indexOf("Kurze Blöcke lesen"))
        assertTrue(QUICKSTART_STEPS.last().text(TutorialFacts()).contains("Einstellungen → Tutorial"))
    }

    @Test
    fun `the settlement step follows the page it explains and no other`() {
        // The page is read first; the step after it is the one that moves on.
        assertEquals(StepKind.TEXT, QUICKSTART_STEPS.first { it.title == "Die Abrechnung" }.kind)
        val step = QUICKSTART_STEPS.first { it.title == "Warum leere Zeit Punkte kostet" }
        fun at(page: String) = TutorialFacts(signals = mapOf(TutorialSignal.REEVALUATION_STEP to page))

        assertFalse(step.ready(at("TASKS")))
        assertFalse(step.ready(at("REWARD")))
        assertTrue(step.ready(at("JOURNAL")))
    }

    // --- the other tutorials ------------------------------------------------

    @Test
    fun `every tutorial stays on its own screen and can simply be read at both ends`() {
        TutorialId.further.forEach { id ->
            assertEquals("${id.name} wanders", 1, id.steps.map { it.stage }.distinct().size)
            assertEquals(StepKind.TEXT, id.steps.first().kind)
            assertEquals(StepKind.TEXT, id.steps.last().kind)
            // None of them has an evening, and none opens a button of the app.
            assertTrue(id.steps.none { it.evening })
            assertTrue(id.steps.all { it.allow.isEmpty() })
        }
        assertEquals(TutorialId.entries.size - 1, TutorialId.further.size)
    }

    @Test
    fun `each feature tutorial waits for the thing it has the user make`() {
        fun task(id: TutorialId) = id.steps.single { it.kind == StepKind.TASK }

        assertFalse(task(TutorialId.GROWTH).ready(TutorialFacts()))
        assertTrue(task(TutorialId.GROWTH).ready(TutorialFacts(growthTasks = 1)))
        assertFalse(task(TutorialId.CONTRACTS).ready(TutorialFacts()))
        assertTrue(task(TutorialId.CONTRACTS).ready(TutorialFacts(contracts = 1)))
        assertFalse(task(TutorialId.REWARDS).ready(TutorialFacts()))
        assertTrue(task(TutorialId.REWARDS).ready(TutorialFacts(rewards = 1)))
        assertFalse(task(TutorialId.EXTRAS).ready(TutorialFacts()))
        assertTrue(task(TutorialId.EXTRAS).ready(TutorialFacts(seen = setOf("${TutorialSignal.EXTRAS_OPEN}=1"))))
    }

    @Test
    fun `the Extras tutorial points at every extra a ToDo has, once each`() {
        val spots = TutorialId.EXTRAS.steps.mapNotNull { it.spot }

        assertEquals(spots.distinct(), spots)
        assertEquals(
            setOf(
                TutorialSpot.EXTRAS,
                TutorialSpot.EXTRA_SOUND,
                TutorialSpot.EXTRA_TRAVEL,
                TutorialSpot.EXTRA_BREAK,
                TutorialSpot.EXTRA_POMODORO,
                TutorialSpot.EXTRA_REMINDER,
                TutorialSpot.EXTRA_DEADLINE,
                TutorialSpot.EXTRA_SUBTASKS,
            ),
            spots.toSet(),
        )
        assertTrue(TutorialId.EXTRAS.steps.first().text(TutorialFacts()).contains(TUTORIAL_EXTRAS_NOTE))
    }

    @Test
    fun `a tutorial is found by its name and an unknown name is none`() {
        assertEquals(TutorialId.REWARDS, TutorialId.named("REWARDS"))
        assertNull(TutorialId.named("NOPE"))
        assertNull(TutorialId.named(null))
    }

    @Test
    fun `once the ToDo is noted the frame moves from the box to its two dots`() {
        val step = QUICKSTART_STEPS.first { it.spot == TutorialSpot.QUICK_ADD }

        assertEquals(TutorialSpot.QUICK_ADD, step.spotAt(TutorialFacts()))
        assertFalse(step.swipeHint(TutorialFacts()))

        val half = TutorialFacts(todoNoted = true)
        assertEquals(TutorialSpot.QUICK_ADD_PAGES, step.spotAt(half))
        assertTrue(step.swipeHint(half))
        assertTrue(step.text(half).contains("zweite"))

        val both = TutorialFacts(todoNoted = true, recurringNoted = true)
        assertEquals(TutorialSpot.QUICK_ADD, step.spotAt(both))
        assertFalse(step.swipeHint(both))
    }

    @Test
    fun `a step without a moving frame keeps the one it names`() {
        val step = QUICKSTART_STEPS.first { it.spot == TutorialSpot.NOW }

        assertEquals(TutorialSpot.NOW, step.spotAt(TutorialFacts(todoNoted = true)))
    }

    @Test
    fun `the lists tutorial frames every list once, in the order of the tab`() {
        val lists = TutorialId.LISTS.steps.mapNotNull { it.spot }.distinct()

        assertEquals(
            listOf(
                TutorialSpot.LIST_COLLECTION,
                TutorialSpot.LIST_WEEK,
                TutorialSpot.LIST_RECURRING,
                TutorialSpot.LIST_TODAY,
                TutorialSpot.LIST_TOMORROW,
                TutorialSpot.LIST_DONE,
                TutorialSpot.LIST_APPOINTMENTS,
                TutorialSpot.LIST_LOCKED,
            ),
            lists,
        )
    }

    @Test
    fun `the lists tutorial has one thing made by holding a heading`() {
        val task = TutorialId.LISTS.steps.single { it.kind == StepKind.TASK }

        assertEquals(TutorialSpot.LIST_COLLECTION, task.spot)
        assertFalse(task.ready(TutorialFacts()))
        assertTrue(task.ready(TutorialFacts(todoNoted = true)))
        // The three lists whose heading makes something say so.
        listOf("Wochenliste", "Wiederkehrende Aufgaben").forEach { title ->
            assertTrue(TutorialId.LISTS.steps.first { it.title == title }.text(TutorialFacts()).contains("gedrückt halten"))
        }
    }
}
