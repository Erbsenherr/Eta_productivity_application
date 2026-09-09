package com.example.erik_iteration_2.data.repository

import com.example.erik_iteration_2.data.local.ItemDao
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Priority
import com.example.erik_iteration_2.domain.model.Stage
import com.example.erik_iteration_2.domain.model.normalizeName
import com.example.erik_iteration_2.domain.recurrence.rulesForWeekdays
import com.example.erik_iteration_2.domain.staging.collectionTimeoutThreshold
import com.example.erik_iteration_2.domain.staging.criticalThreshold
import com.example.erik_iteration_2.domain.staging.isCriticalInCollection
import com.example.erik_iteration_2.domain.staging.isStaleInCollection
import com.example.erik_iteration_2.domain.staging.lockedForSperrliste
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import java.util.UUID

/** Outcome of trying to create a ToDo, which the Sperrliste can veto. */
sealed interface AddItemResult {
    data class Added(val item: Item) : AddItemResult
    data class BlockedByLock(val lockedUntil: Instant) : AddItemResult
}

class ItemRepository(
    private val itemDao: ItemDao,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    fun observeStage(stage: Stage): Flow<List<Item>> = itemDao.observeByStage(stage)

    fun observeOpenDeadlines(): Flow<List<Item>> = itemDao.observeOpenDeadlines()

    /**
     * Sammelliste entries about to be banned onto the Sperrliste — dashboard box 0.
     * The SQL window is widened slightly and narrowed exactly in Kotlin, so the
     * calendar-aware rule stays in one place.
     */
    fun observeCriticalCollectionItems(): Flow<List<Item>> {
        val now = clock.now()
        return itemDao
            .observeCollectionEnteredBefore(criticalThreshold(now, timeZone))
            .map { items -> items.filter { it.isCriticalInCollection(clock.now(), timeZone) } }
    }

    fun observeRecurringDefinitions(): Flow<List<Item>> = itemDao.observeRecurringDefinitions()

    suspend fun findById(id: String): Item? = itemDao.findById(id)

    /**
     * Quick-Add from the dashboard. Refused when the same name is still banned,
     * so the Sperrliste cannot be circumvented by simply retyping the ToDo.
     */
    suspend fun addQuickTodo(name: String): AddItemResult {
        val now = clock.now()
        itemDao.findActiveLock(normalizeName(name), now)?.let { locked ->
            return AddItemResult.BlockedByLock(locked.lockedUntil ?: now)
        }
        val item = Item.newQuickTodo(name, now)
        itemDao.upsert(item)
        return AddItemResult.Added(item)
    }

    /**
     * The recurring Quick-Add: also name only, and refused by the same lock.
     *
     * It lands in the Sammelliste rather than the schedule, because it has no
     * rule yet — asking for weekdays and a duration at the moment of the thought
     * is what a Quick-Add is supposed to spare the user.
     */
    suspend fun addQuickRecurring(name: String): AddItemResult {
        val now = clock.now()
        itemDao.findActiveLock(normalizeName(name), now)?.let { locked ->
            return AddItemResult.BlockedByLock(locked.lockedUntil ?: now)
        }
        val item = Item.newQuickRecurring(name, now)
        itemDao.upsert(item)
        return AddItemResult.Added(item)
    }

    suspend fun add(item: Item): AddItemResult {
        val now = clock.now()
        itemDao.findActiveLock(item.normalizedName, now)?.let { locked ->
            return AddItemResult.BlockedByLock(locked.lockedUntil ?: now)
        }
        itemDao.upsert(item)
        return AddItemResult.Added(item)
    }

    suspend fun update(item: Item) {
        itemDao.upsert(item.copy(updatedAt = clock.now()))
    }

    suspend fun delete(item: Item) = itemDao.delete(item)

    /**
     * Fills in the attributes a Quick-Add ToDo was created without.
     *
     * [travelBefore] and [breakAfter] are defaults for the blocks this will
     * produce rather than part of the task itself; [endSound] says whether the
     * end of its planned time announces itself.
     */
    suspend fun concretize(
        item: Item,
        category: Category,
        priority: Priority,
        targetDate: LocalDate,
        estimatedDuration: Duration,
        travelBefore: Duration? = null,
        breakAfter: Duration? = null,
        endSound: Boolean = item.endSound,
    ) {
        update(
            item.concretized(
                category = category,
                priority = priority,
                targetDate = targetDate,
                estimatedDuration = estimatedDuration,
                travelBefore = travelBefore,
                breakAfter = breakAfter,
                endSound = endSound,
            ),
        )
    }

    /**
     * Fills in what a bare recurring note was missing, and lays it down.
     *
     * Several weekdays become several definitions — see [rulesForWeekdays] — so
     * the note that was one row can leave as two or three. The one that already
     * exists is updated rather than replaced, which keeps any blocks that somehow
     * point at it, and the rest are copies with fresh ids.
     *
     * It leaves the Sammelliste for `Stage.DAY`, where recurring definitions
     * live, and `enteredCollectionAt` is cleared with it: the one-month clock
     * measures hoarding, and this is no longer being hoarded.
     */
    suspend fun concretizeRecurring(
        item: Item,
        category: Category?,
        weekdays: Set<DayOfWeek>,
        startTime: LocalTime,
        duration: Duration,
        travelBefore: Duration? = null,
        breakAfter: Duration? = null,
        endSound: Boolean = item.endSound,
    ) {
        val rules = rulesForWeekdays(weekdays)
        if (rules.isEmpty()) return

        val now = clock.now()
        val base = item.copy(
            stage = Stage.DAY,
            category = category,
            startTime = startTime,
            estimatedDuration = duration,
            travelBefore = travelBefore,
            breakAfter = breakAfter,
            endSound = endSound,
            enteredCollectionAt = null,
            updatedAt = now,
        )
        itemDao.upsertAll(
            listOf(base.copy(recurrenceRule = rules.first())) +
                rules.drop(1).map { rule ->
                    base.copy(
                        id = UUID.randomUUID().toString(),
                        recurrenceRule = rule,
                        createdAt = now,
                    )
                },
        )
    }

    /** Weekly goals of an earlier cycle that are still in flight. */
    fun observeUnfinishedWeekGoals(cycleStart: LocalDate): Flow<List<Item>> =
        itemDao.observeUnfinishedWeekGoals(cycleStart)

    /**
     * Takes a ToDo on as a goal of the cycle beginning [cycleStart].
     *
     * The stamp is what lets the next cycle ask what this one committed to: the
     * week list itself empties as soon as things are planned into days.
     */
    suspend fun takeIntoWeek(item: Item, cycleStart: LocalDate) {
        val now = clock.now()
        itemDao.upsert(item.copy(stage = Stage.WEEK, weekStartedOn = cycleStart, updatedAt = now))
    }

    /**
     * Retires one-shot ToDos whose occurrence on [date] was completed.
     *
     * Stage.DONE was documented as the stage that takes them out of the active
     * lists but nothing ever set it — completion lives on the block. The week-goal
     * gate needs to know an item is finished, so this closes that gap where it
     * belongs: at settlement, the moment the day is accounted for.
     */
    suspend fun retireCompletedTodos(date: LocalDate) {
        val now = clock.now()
        val done = itemDao.findCompletedTodosOn(date)
            .map { it.copy(stage = Stage.DONE, completedAt = it.completedAt ?: now, updatedAt = now) }
        if (done.isNotEmpty()) itemDao.upsertAll(done)
    }

    /**
     * Stages an item forward. Re-entering the Sammelliste restarts the timeout
     * clock, which is what makes a deliberately deferred ToDo safe from the ban.
     */
    suspend fun moveTo(item: Item, stage: Stage) {
        val now = clock.now()
        itemDao.upsert(
            item.copy(
                stage = stage,
                enteredCollectionAt = if (stage == Stage.COLLECTION) now else item.enteredCollectionAt,
                // Handing it back gives up the commitment, so it stops counting
                // against the next cycle's gate.
                weekStartedOn = if (stage == Stage.COLLECTION) null else item.weekStartedOn,
                updatedAt = now,
            ),
        )
    }

    /**
     * Moves every ToDo that overstayed the Sammelliste onto the Sperrliste.
     * Runs as part of the planning phases. Returns how many were banned.
     */
    suspend fun sweepStaleCollectionItems(): Int {
        val now = clock.now()
        val stale = itemDao
            .findStaleCollectionItems(collectionTimeoutThreshold(now, timeZone))
            .filter { it.isStaleInCollection(now, timeZone) }
        if (stale.isEmpty()) return 0
        itemDao.upsertAll(stale.map { it.lockedForSperrliste(now, timeZone) })
        return stale.size
    }
}
