package com.example.eta.data.repository

import com.example.eta.data.local.ItemDao
import com.example.eta.domain.growth.GrowthSetting
import com.example.eta.domain.growth.withGrowth
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemExtras
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.withExtras
import com.example.eta.domain.recurrence.Rhythm
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.domain.recurrence.reassignRows
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.UUID

/** What an edited standing task is to become. */
data class RecurringEdit(
    val name: String,
    val note: String?,
    val category: Category?,
    val weekdays: Set<DayOfWeek>,
    val startTime: LocalTime,
    val duration: Duration,
    val travelBefore: Duration?,
    val returnAfter: Duration?,
    val breakAfter: Duration?,
    val endSound: Boolean,
    /** The four newer switches of the "Extras" box, in one value. */
    val extras: ItemExtras = ItemExtras(),
    /**
     * Growth, or null for an ordinary standing task.
     *
     * When this is set, [duration] is the length the task is at *now* rather than
     * an answer of its own: growing owns it, and `withGrowth` is what decides it —
     * see `GrowthTask.kt`.
     */
    val growth: GrowthSetting? = null,
    /**
     * The steps inside the task, or null to leave whatever it has alone.
     *
     * Written to every row of the family, because the standing schedule groups a
     * task by its attributes: two weekdays with different lists would start
     * showing up as two tasks.
     */
    val subtasks: List<SubtaskDraft>? = null,
    /** Tasks this group swallowed as steps; retired when it is saved. */
    val folded: Collection<String> = emptyList(),
)

/**
 * Changing the standing schedule after the fact — the task itself, not one day
 * of it.
 *
 * **Confirmed days keep what they have.** Today is being lived, and tomorrow, once
 * its plan is confirmed, is a promise already made; an edit to the definition
 * reaching into either would rewrite a plan behind the user's back. Every
 * occurrence after that is still only laid down ahead of time — the schedule
 * materializes three weeks out — so those still-open ones are taken off and laid
 * down again from the new definition. Leaving them would make an edit show up
 * three weeks late, which is not what "only future ones change" means.
 *
 * Completed and called-off occurrences are never touched, on any day: they are
 * the record of what happened.
 *
 * **Definitions are retired, never deleted.** `planned_blocks` cascades on delete,
 * so deleting a definition would take its past completions out of the
 * Erfolgsliste — the same reason the questionnaire retires rather than deletes.
 */
class RecurringTaskService(
    private val itemDao: ItemDao,
    private val subtaskRepository: SubtaskRepository,
    private val planRepository: PlanRepository,
    private val scheduleMaintenance: ScheduleMaintenance,
    private val clock: Clock = Clock.System,
) {

    fun observeDefinitions(): Flow<List<Item>> = itemDao.observeRecurringDefinitions()

    /**
     * The last day whose occurrences an edit leaves alone: today, or tomorrow once
     * tomorrow's plan is confirmed.
     *
     * Asked of [PlanRepository], where it now lives: the growth increment and the
     * dynamic placement pass draw the same line, and three copies of it would be
     * three chances for one of them to drift.
     */
    suspend fun lastKeptDay(): LocalDate = planRepository.lastKeptDay()

    /**
     * Writes an edited group of definitions — one per weekday, as always.
     *
     * The rows are re-read by id first rather than trusting the screen's copies,
     * which may be older than the last save. Each weekday keeps the row it already
     * had where there is one, so holiday rules and past completions stay attached
     * to the same task; a dropped weekday's row is retired, a new weekday gets a
     * leftover row or a fresh one. The rhythm — weekly, fortnightly, monthly — is
     * kept, since the form only asks for the weekdays.
     */
    suspend fun saveGroup(ids: Collection<String>, edit: RecurringEdit) {
        val existing = ids.mapNotNull { itemDao.findById(it) }.filter { it.completedAt == null }
        val representative = existing.firstOrNull() ?: return
        val rhythm = Rhythm.of(representative.recurrenceRule ?: return)
        val rules = rhythm.rulesFor(edit.weekdays)
        if (rules.isEmpty()) return

        val now = clock.now()
        val byId = existing.associateBy { it.id }
        val (assigned, leftover) = reassignRows(existing, rules) { UUID.randomUUID().toString() }

        val renamed = if (edit.name != representative.name) {
            representative.renamed(edit.name, now)
        } else {
            representative
        }
        val written = assigned.map { (id, rule) ->
            val row = byId[id]
            renamed.copy(
                id = id,
                // A row keeps its own role and creation date; a new one inherits the
                // group's role, since it is the same task on another day.
                role = row?.role ?: representative.role,
                createdAt = row?.createdAt ?: now,
                stage = Stage.DAY,
                note = edit.note,
                category = edit.category,
                recurrenceRule = rule,
                startTime = edit.startTime,
                estimatedDuration = edit.duration,
                travelBefore = edit.travelBefore,
                returnAfter = edit.returnAfter,
                breakAfter = edit.breakAfter,
                endSound = edit.endSound,
                completedAt = null,
                updatedAt = now,
            )
                .withExtras(edit.extras)
                // Last, and it may overrule the duration above: for a growth task
                // the current length belongs to growing rather than to the form.
                .withGrowth(edit.growth)
        }
        val retired = leftover.map { it.copy(completedAt = now, updatedAt = now) }

        itemDao.upsertAll(written + retired)
        edit.subtasks?.let { drafts -> written.forEach { subtaskRepository.save(it.id, drafts) } }
        itemDao.retireFolded(edit.folded, now)
        relayOccurrences((written + retired).map { it.id })
    }

    /**
     * A standing task made from nothing — one definition per weekday, as always.
     *
     * The path the Growth-Tasks tab needs, and the first one there has ever been:
     * every other standing task in the app arrives either from the questionnaire
     * or from a bare note the evening concretizes, and neither of those is a
     * button that says "create". [growthOrder] is where it lands in that tab.
     *
     * Returns the ids written, so the caller can select what it just made.
     */
    suspend fun createGroup(
        edit: RecurringEdit,
        rhythm: Rhythm = Rhythm.Weekly,
        growthOrder: Int? = null,
    ): List<String> {
        val rules = rhythm.rulesFor(edit.weekdays)
        if (rules.isEmpty()) return emptyList()

        val now = clock.now()
        val base = Item(
            type = ItemType.RECURRING,
            name = edit.name,
            stage = Stage.DAY,
            category = edit.category,
            note = edit.note,
            startTime = edit.startTime,
            estimatedDuration = edit.duration,
            travelBefore = edit.travelBefore,
            returnAfter = edit.returnAfter,
            breakAfter = edit.breakAfter,
            endSound = edit.endSound,
            growthOrder = growthOrder,
            createdAt = now,
            updatedAt = now,
        )
            .withExtras(edit.extras)
            .withGrowth(edit.growth)

        val rows = rules.map { rule ->
            base.copy(id = UUID.randomUUID().toString(), recurrenceRule = rule)
        }
        itemDao.upsertAll(rows)
        edit.subtasks?.let { drafts -> rows.forEach { subtaskRepository.save(it.id, drafts) } }
        itemDao.retireFolded(edit.folded, now)
        // Laid down at once: a task that exists without a single occurrence while
        // the user is looking at the screen that just created it reads as broken.
        scheduleMaintenance.topUp()
        return rows.map { it.id }
    }

    /**
     * Ends a standing task: retired, and its open occurrences after the confirmed
     * days taken off. What already happened stays in the Erfolgsliste.
     */
    suspend fun endGroup(ids: Collection<String>) {
        val now = clock.now()
        val rows = ids.mapNotNull { itemDao.findById(it) }
            .filter { it.completedAt == null }
            .map { it.copy(completedAt = now, updatedAt = now) }
        if (rows.isEmpty()) return
        itemDao.upsertAll(rows)
        relayOccurrences(rows.map { it.id })
    }

    /** See [ScheduleMaintenance.relayOccurrences]; here for the setup, which holds no other handle. */
    suspend fun relayOccurrences(itemIds: List<String>) =
        scheduleMaintenance.relayOccurrences(itemIds)
}
