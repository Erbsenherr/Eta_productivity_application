package com.example.eta.data.repository

import com.example.eta.data.local.ItemDao
import com.example.eta.domain.growth.GrowthFamily
import com.example.eta.domain.growth.QuantityFamily
import com.example.eta.domain.growth.advancedByCompletion
import com.example.eta.domain.growth.growthFamily
import com.example.eta.domain.growth.growthOrdering
import com.example.eta.domain.growth.hasQuantity
import com.example.eta.domain.growth.isGrowthTask
import com.example.eta.domain.growth.quantityFamily
import com.example.eta.domain.model.Item
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Growth tasks: the order they hold, and the one thing that makes them grow.
 *
 * The growth itself is a single field moving — `estimatedDuration`, the current
 * length — which is why nothing else in the app had to learn about the feature.
 * What needs a service is the two things around it: the increment has to happen
 * at the right moment and exactly once, and the occurrences already laid down
 * ahead have to be laid down again afterwards, or the new length would only
 * appear three weeks from now.
 */
class GrowthService(
    private val itemDao: ItemDao,
    private val planRepository: PlanRepository,
    private val scheduleMaintenance: ScheduleMaintenance,
    private val clock: Clock = Clock.System,
) {

    /** Every live growth task, in the order the Growth-Tasks tab shows them. */
    fun observeGrowthTasks(): Flow<List<Item>> =
        itemDao.observeRecurringDefinitions().map { definitions ->
            definitions.filter { it.isGrowthTask }.sortedWith(growthOrdering)
        }

    /** The number a newly created growth task takes: last in the list. */
    suspend fun nextOrder(): Int =
        (itemDao.findRecurringDefinitions().filter { it.isGrowthTask }
            .mapNotNull { it.growthOrder }
            .maxOrNull() ?: -1) + 1

    /**
     * Writes the order the Growth-Tasks tab was dragged into.
     *
     * The number is on every definition of a task, not only on the row the list
     * draws: a task on Tuesday and Thursday is two rows in the database, and a
     * priority that applied to one of them would decide Tuesday's slot and leave
     * Thursday's to chance.
     */
    suspend fun reorder(orderedIds: List<List<String>>) {
        val now = clock.now()
        val rows = orderedIds.flatMapIndexed { index, group ->
            group.mapNotNull { id ->
                itemDao.findById(id)?.takeIf { it.growthOrder != index }
                    ?.copy(growthOrder = index, updatedAt = now)
            }
        }
        if (rows.isEmpty()) return
        itemDao.upsertAll(rows)
        // The order is what settles a contested slot, so changing it can change
        // where every dynamic growth task of every open day sits.
        scheduleMaintenance.relayDynamicGrowth()
    }

    /**
     * One confirmed completion counted for every growth task and every counted
     * task completed on [date] — and, where the update condition is met, the
     * increment: a step longer, a step more.
     *
     * Hung on the **settlement** rather than on the checkbox, because that is
     * what the user called a confirmed completion — the same reasoning that keeps
     * the harvest out of the dashboard's tick. A day that was already settled is
     * skipped, which is what stops a reevaluation opened twice from counting a
     * completion twice; no column is needed for it, since `DayPlan.settledAt`
     * already records that the day has been accounted for.
     *
     * The update condition ("Inkrement alle X Absolvierungen") is a count stored
     * beside the length: `growthProgress` and `quantityProgress` say how many
     * completions have been counted towards the next step. See
     * [advancedByCompletion].
     *
     * Returns the tasks whose length actually moved, for the evening to be able to
     * say so.
     */
    suspend fun advanceFor(date: LocalDate): List<Item> {
        if (planRepository.findDayPlan(date)?.isSettled == true) return emptyList()

        val now = clock.now()
        // Counted once per **task**, not per row. A task on Tuesday and Thursday is
        // two definitions; advancing only the one that was completed would give the
        // two different lengths and counts, which the standing schedule then shows
        // as two separate tasks. See [GrowthFamily] and [QuantityFamily].
        val advanced = planRepository.completedOn(date)
            .map { it.item }
            .filter { it.isGrowthTask || it.hasQuantity }
            .distinctBy { it.familyKey }
            .associate { item -> item.familyKey to item.advancedByCompletion() }
        if (advanced.isEmpty()) return emptyList()

        val rows = itemDao.findRecurringDefinitions()
            .filter { it.isGrowthTask || it.hasQuantity }
        val changed = rows.mapNotNull { row ->
            val next = advanced[row.familyKey] ?: return@mapNotNull null
            val updated = row.copy(
                estimatedDuration = next.estimatedDuration,
                growthProgress = next.growthProgress,
                quantity = next.quantity,
                quantityProgress = next.quantityProgress,
            )
            if (updated == row) null else row to updated.copy(updatedAt = now)
        }
        if (changed.isEmpty()) return emptyList()

        itemDao.upsertAll(changed.map { it.second })
        val grown = changed
            .filter { (before, after) -> before.estimatedDuration != after.estimatedDuration }
            .map { it.second }
        // The occurrences already lying ahead still carry the old length. Taking
        // the open ones off and laying them down again is what makes tomorrow's
        // block the longer one rather than the one three weeks out. A count needs
        // none of this: every occurrence reads it off the definition.
        scheduleMaintenance.relayOccurrences(grown.map { it.id })
        return grown
    }
}

/** Rows that advance together: the same growth task and the same count. */
private val Item.familyKey: Pair<GrowthFamily, QuantityFamily>
    get() = growthFamily to quantityFamily
