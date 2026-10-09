package com.example.eta.data.repository

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.ItemExtras
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.stampedWith
import com.example.eta.domain.model.withExtras
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.canPlace
import com.example.eta.domain.planning.claimsItsSlot
import com.example.eta.domain.planning.shortenedBefore
import com.example.eta.domain.planning.startedAfter
import com.example.eta.domain.planning.overlaps
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.datetime.LocalTime

/** What an event is turned into. No priority: the date already outranks one. */
data class AppointmentDetails(
    val category: Category,
    val start: LocalTime,
    val duration: Duration,
    val travelBefore: Duration? = null,
    val returnAfter: Duration? = null,
    val breakAfter: Duration? = null,
    val endSound: Boolean = true,
    /** The rest of the Extras box: the pomodoro default and the reminder. */
    val extras: ItemExtras = ItemExtras(),
)

/**
 * One thing the appointment landed on, and every way out of that.
 *
 * The two whole-activity answers — call it off, carry it into the week — always
 * apply. The two partial ones do not, and whether they do is arithmetic rather
 * than taste, so it is worked out here instead of being offered and then refused:
 *
 * - [shortened] exists when the block begins before the appointment does, so
 *   there is a stretch worth keeping in front of it.
 * - [pushed] exists when it runs past the appointment's end *and* the new
 *   position is free of everything else on the day.
 *
 * Both are null for a block the appointment swallows whole — the case the user
 * named: there is nothing to shorten *to* and nothing to wait *for*.
 */
data class ConflictingBlock(
    val entry: BlockWithItem,
    val shortened: PlannedBlock?,
    val pushed: PlannedBlock?,
)

/**
 * An imported appointment, and whatever it landed on top of.
 *
 * The collisions come back rather than being resolved: an appointment out of the
 * calendar is a fact, so it takes its slot either way, but what happens to the
 * Sport session it now sits on is a decision only the user can make.
 */
data class ImportResult(
    val item: Item,
    val block: PlannedBlock,
    val conflicts: List<ConflictingBlock>,
)

/**
 * Turning a calendar event into a card, and sorting out what it collides with.
 *
 * Spans three repositories, which is why it is a service rather than a method on
 * any of them: it writes an [Item], a [PlannedBlock] and the decision on the
 * [CalendarEvent] that links the two back to Google.
 *
 * The link is the point. Without it a second sync could not tell "this event
 * again" from "a new event", and every refresh would lay the same appointment
 * down once more — the same trap `expandRecurring` solves with its set of
 * already-materialized pairs, and the same fix.
 */
class CalendarImportService(
    private val itemRepository: ItemRepository,
    private val planRepository: PlanRepository,
    private val calendarRepository: CalendarRepository,
    private val dayClosingService: DayClosingService,
    private val weekPlanningService: WeekPlanningService,
    private val clock: Clock = Clock.System,
) {

    /**
     * Makes the card and puts it on its day.
     *
     * Placed at the hour the calendar gives, not at the next free one: an
     * appointment does not slide. That is also why the block is
     * [BlockOrigin.CALENDAR_IMPORT] — square-cornered and immovable in the
     * planner, though its time can still be corrected in the edit dialog, exactly
     * as a recurring occurrence can.
     *
     * The Sperrliste is not consulted. It exists to stop an idea being hoarded and
     * retyped; an appointment somebody put in a calendar is a fact about the
     * world, and refusing to show it would not make it go away.
     */
    suspend fun import(event: CalendarEvent, details: AppointmentDetails): ImportResult {
        val now = clock.now()

        val item = Item(
            type = ItemType.TODO,
            name = event.title,
            stage = Stage.DAY,
            category = details.category,
            // Fixed to a day and an hour, which says something stronger than any
            // of the four tiers — the same reasoning as a ToDo dropped straight
            // onto a slot in the planner. A priority is carried only because
            // `isConcretized` asks a ToDo for one.
            priority = Priority.URGENT_MUST,
            targetDate = event.date,
            estimatedDuration = details.duration,
            travelBefore = details.travelBefore,
            returnAfter = details.returnAfter,
            breakAfter = details.breakAfter,
            endSound = details.endSound,
            createdAt = now,
            updatedAt = now,
        ).withExtras(details.extras)
        itemRepository.addWithoutLockCheck(item)

        val block = PlannedBlock(
            itemId = item.id,
            date = event.date,
            start = details.start,
            plannedDuration = details.duration,
            origin = BlockOrigin.CALENDAR_IMPORT,
            travelBefore = details.travelBefore,
            returnAfter = details.returnAfter,
            breakAfter = details.breakAfter,
            createdAt = now,
            updatedAt = now,
        ).stampedWith(item)
        planRepository.addBlock(block)
        calendarRepository.markImported(event, item.id)

        // Whatever was already standing on those minutes. Containers, not tasks:
        // a journey is time the day genuinely spends, and an appointment planned
        // into one would be planned into a drive already under way.
        val standing = planRepository.findDay(event.date).filter { it.claimsItsSlot() }
        val obstacles = standing.map { it.block }

        val conflicts = standing
            .filter { it.block.id != block.id && it.block.overlaps(block) }
            .map { entry ->
                // Only for a block still open. Shortening something that already
                // happened would be rewriting the record of the day rather than
                // making room in it.
                val open = entry.block.isOpen
                ConflictingBlock(
                    entry = entry,
                    shortened = if (open) entry.block.shortenedBefore(block) else null,
                    // Offered only where it actually lands clear: `canPlace`
                    // compares against everything still standing, the new
                    // appointment included, so waiting cannot mean waiting on top
                    // of something else.
                    pushed = if (open) {
                        entry.block.startedAfter(block)?.takeIf { canPlace(obstacles, it) }
                    } else {
                        null
                    },
                )
            }

        return ImportResult(item = item, block = block, conflicts = conflicts)
    }

    /**
     * Cuts a block short so it is over before the appointment starts.
     *
     * Re-reads the row rather than writing back the copy the dialog is holding:
     * that copy was made when the appointment was imported, and by the time the
     * user answers, an earlier answer in the same dialog may have moved things.
     * A caller handed a row cannot tell how old it is — see *Step 12*.
     *
     * Returns false when the row is gone. The shortening itself cannot fail: it
     * only ever makes a block smaller, which can collide with nothing.
     */
    suspend fun shortenConflicting(entry: BlockWithItem, to: Duration): Boolean {
        val fresh = planRepository.findBlock(entry.block.id) ?: return false
        planRepository.addBlock(
            fresh.copy(plannedDuration = to, actualDuration = null, updatedAt = clock.now()),
        )
        return true
    }

    /**
     * Starts a block again once the appointment is over.
     *
     * Re-checked against the day as it stands now, not as it stood at import:
     * answering two conflicts in a row can fill the very stretch the second one
     * was going to move into. False means the offer no longer holds, and the
     * conflict stays on the screen with its other answers intact — better than
     * moving the block somewhere nobody chose.
     */
    suspend fun moveConflictingAfter(entry: BlockWithItem, to: LocalTime): Boolean {
        val fresh = planRepository.findBlock(entry.block.id) ?: return false
        val moved = fresh.copy(start = to, updatedAt = clock.now())

        val obstacles = planRepository.findDay(fresh.date)
            .filter { it.claimsItsSlot() }
            .map { it.block }
        if (!canPlace(obstacles, moved)) return false

        planRepository.addBlock(moved)
        return true
    }

    /**
     * The first answer to a collision: the other thing is simply not happening.
     *
     * A cancellation rather than a deletion, for the reason it always is —
     * expansion runs forward from today and would lay a deleted occurrence down
     * again. Decided in advance, so it costs nothing; only a day already under way
     * can break a promise.
     */
    suspend fun cancelConflicting(entry: BlockWithItem) {
        planRepository.discard(entry.block)
    }

    /**
     * The second answer: do it another day.
     *
     * A dragged ToDo goes back to the week list it came from. A recurring
     * occurrence cannot — there is nothing there to return to — so it is called
     * off and a "Nachholen von …" takes its place in the week list, which is the
     * machinery the evening reevaluation already uses for exactly this. Idempotent
     * through `makeUpItemId`: answering the same collision twice makes one card.
     */
    suspend fun carryConflictingIntoWeek(entry: BlockWithItem): Item? {
        val cycle = weekPlanningService.cycleStart()

        if (entry.block.isHandPlaced && entry.item.type == ItemType.TODO) {
            planRepository.removeBlock(entry.block)
            itemRepository.takeIntoWeek(entry.item, cycle)
            return entry.item
        }

        // The catch-up is written first, because it writes to the block too —
        // `makeUpItemId`. Calling off the stale row afterwards would put that
        // field straight back to null, so the row is re-read in between.
        val makeUp = dayClosingService.makeUp(entry)
        planRepository.discard(planRepository.findBlock(entry.block.id) ?: entry.block)
        itemRepository.takeIntoWeek(makeUp, cycle)
        return makeUp
    }
}
