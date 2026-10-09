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
import com.example.eta.domain.tutorial.TUTORIAL_DONE_IDS
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
import com.example.eta.domain.tutorial.tutorialItems
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

    private class FixedClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    /** The example day as the seed lays it down: one block per task, today. */
    private fun exampleDay(): List<BlockWithItem> {
        val items = tutorialItems(today.dayOfWeek, now)
        val byId = items.associateBy { it.id }
        return expandRecurring(items, today, today, emptySet(), now)
            .map { BlockWithItem(it, byId.getValue(it.itemId)) }
    }

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
    fun `every example task lays an occurrence down today`() {
        val day = exampleDay()

        assertEquals(4, day.size)
        assertTrue(day.all { it.item.isConcretized })
        assertTrue(day.all { it.block.origin == BlockOrigin.RECURRING })
    }

    @Test
    fun `the daily three stand on tomorrow as well and the weekly mail does not`() {
        val items = tutorialItems(today.dayOfWeek, now)
        val tomorrow = today.plus(DatePeriod(days = 1))

        val ids = expandRecurring(items, tomorrow, tomorrow, emptySet(), now).map { it.itemId }.toSet()

        assertEquals(TUTORIAL_DONE_IDS, ids)
    }

    @Test
    fun `at the tutorial's hour work is running and the cat is next`() {
        val state = nowAndNext(exampleDay(), TUTORIAL_MORNING.minuteOfDay())

        assertEquals(TUTORIAL_WORK_ID, state.current?.entry?.item?.id)
        assertEquals("Katze füttern", state.next?.entry?.item?.name)
    }

    @Test
    fun `the practice setup lays down nothing of its own and shows what a newcomer sees`() {
        val setup = tutorialSetup(now)

        // Bed preparation and the morning aside, which the seed never writes.
        assertTrue(setup.recurringItems(now).all { it.id.startsWith("setup:bedprep") || it.id.startsWith("setup:morning") })
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
        val day = exampleDay().completing(TUTORIAL_DONE_IDS)
            .changing(TUTORIAL_MAIL_ID) { it.copy(discardedAt = now, makeUpItemId = "x") }

        val facts = tutorialFacts(emptyList(), emptyList(), day, emptyList(), false)

        assertTrue(facts.completedToday.containsAll(TUTORIAL_DONE_IDS))
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
        val three = exampleDay().completing(TUTORIAL_DONE_IDS)
        val four = exampleDay().completing(TUTORIAL_DONE_IDS + TUTORIAL_MAIL_ID)

        assertTrue(step.ready(tutorialFacts(emptyList(), emptyList(), three, emptyList(), false)))
        val facts = tutorialFacts(emptyList(), emptyList(), four, emptyList(), false)
        assertFalse(step.ready(facts))
        assertEquals(2, step.checks(facts).size)
    }

    @Test
    fun `a screen's own buttons are opened by the step that asks for them and by no other`() {
        fun opening(gate: String) = QUICKSTART_STEPS.filter { gate in it.allow }.map { it.title }

        assertEquals(listOf("Was nicht geklappt hat"), opening(TutorialGate.REEVALUATION_DISCARD + TUTORIAL_MAIL_ID))
        assertEquals(listOf("Passt das noch?"), opening(TutorialGate.REEVALUATION_SETTLE))
        assertEquals(listOf("Dauer"), opening(TutorialGate.CONCRETIZE_TODO))
        assertEquals(listOf("Wiederholen bis"), opening(TutorialGate.CONCRETIZE_RECURRING))
        assertEquals(listOf(QUICKSTART_STEPS.last().title), opening(TutorialGate.PLANNER_CONFIRM))
        assertEquals(listOf("Geschafft"), opening(TutorialGate.CONCRETIZE_DONE))
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
        val day = exampleDay().completing(TUTORIAL_DONE_IDS)
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
        assertTrue(titles.indexOf("Der zweite Revolver") > titles.indexOf("Der Tagesplaner"))
        assertTrue(QUICKSTART_STEPS.last().text(TutorialFacts()).contains("Einstellungen → Tutorial"))
    }

    @Test
    fun `the settlement step follows the page it explains and no other`() {
        val step = QUICKSTART_STEPS.first { it.title == "Die Abrechnung" }
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
}
