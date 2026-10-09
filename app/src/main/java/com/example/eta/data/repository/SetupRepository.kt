package com.example.eta.data.repository

import com.example.eta.data.local.ItemDao
import com.example.eta.data.local.ResetDao
import com.example.eta.data.local.SetupDao
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.setup.isMorningRoutine
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.domain.subtask.drafts
import com.example.eta.domain.setup.isOwnedBySettings
import com.example.eta.domain.setup.SETUP_ID
import com.example.eta.domain.setup.SETUP_ITEM_ID_PREFIX
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.recurringItems
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** How far ahead the first schedule is materialized, so the app is not empty afterwards. */
private const val INITIAL_HORIZON_DAYS = 14

class SetupRepository(
    private val setupDao: SetupDao,
    private val itemDao: ItemDao,
    private val resetDao: ResetDao,
    private val planRepository: PlanRepository,
    private val vacationRepository: VacationRepository,
    private val recurringTaskService: RecurringTaskService,
    private val subtaskRepository: SubtaskRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    /** Null until the questionnaire has been answered — what gates the app's first screen. */
    fun observe(): Flow<UserSetup?> = setupDao.observe(SETUP_ID)

    suspend fun find(): UserSetup? = setupDao.find(SETUP_ID)

    /**
     * Stores the questionnaire's answers and lays the standing schedule down from
     * them — **once**, when the questionnaire is answered.
     *
     * Only the questionnaire calls this. Afterwards the tasks it created are
     * ordinary standing tasks, edited on the Listen tab, and answering again from
     * the settings would overwrite those edits; see [saveSettings].
     *
     * The generated definitions carry deterministic ids, so answering again
     * updates them. Ones that no longer follow from the answers are retired via
     * `completedAt` rather than deleted: deleting would cascade to their blocks
     * and take past completions out of the Erfolgsliste with them.
     */
    suspend fun complete(setup: UserSetup) {
        val now = clock.now()
        setupDao.upsert(setup.copy(id = SETUP_ID, completedAt = now, updatedAt = now))

        val generated = setup.recurringItems(now)
        val stillWanted = generated.map { it.id }.toSet()
        val obsolete = itemDao.findByIdPrefix(SETUP_ITEM_ID_PREFIX)
            .filter { it.id !in stillWanted && it.completedAt == null }
            .map { it.copy(completedAt = now, updatedAt = now) }

        itemDao.upsertAll(generated + obsolete)

        val today = now.toLocalDateTime(timeZone).date

        // Retiring the definition is not enough: its occurrences are already on
        // the calendar. Without this, moving an answer leaves the old blocks
        // standing next to the new ones for as far ahead as they were laid down.
        if (obsolete.isNotEmpty()) {
            planRepository.clearUpcoming(obsolete.map { it.id }, today)
        }

        planRepository.materializeRecurring(
            definitions = itemDao.findRecurringDefinitions(),
            from = today,
            to = today.plus(DatePeriod(days = INITIAL_HORIZON_DAYS)),
            // A holiday already entered must not be undone by regenerating the
            // schedule; expansion has to see it here too.
            vacations = vacationRepository.plansFrom(today),
        )
    }

    /**
     * Stores the standing configuration from the settings tab.
     *
     * Of the recurring definitions, only the two the settings still own are
     * regenerated — bed preparation and the morning, see [isOwnedBySettings].
     * Everything else lives on the Listen tab now; regenerating it here would undo
     * whatever was changed there.
     *
     * Their occurrences follow the same rule as any edit to a standing task: the
     * confirmed days keep theirs, every day after is laid down again. Before, a
     * changed wake time only reached the schedule three weeks later.
     */
    suspend fun saveSettings(setup: UserSetup) {
        val now = clock.now()
        setupDao.upsert(setup.copy(id = SETUP_ID, updatedAt = now))

        val existing = itemDao.findByIdPrefix(SETUP_ITEM_ID_PREFIX)
            .filter { isOwnedBySettings(it.id) }
            .associateBy { it.id }
        val generated = setup.recurringItems(now)
            .filter { isOwnedBySettings(it.id) }
            .map { item -> existing[item.id]?.let { item.copy(createdAt = it.createdAt) } ?: item }
        val obsolete = existing.values
            .filter { old -> generated.none { it.id == old.id } && old.completedAt == null }
            .map { it.copy(completedAt = now, updatedAt = now) }

        // Read before the rows are swapped: switching the weekend night on or
        // off replaces `setup:morning` by one row per night, or the reverse, and
        // the steps have to follow the routine onto whichever rows it now has.
        val steps = morningDefinitions().firstNotNullOfOrNull { definition ->
            subtaskRepository.forItem(definition.id).takeIf { it.isNotEmpty() }
        }

        itemDao.upsertAll(generated + obsolete)
        if (steps != null) saveMorningSteps(steps.drafts())
        recurringTaskService.relayOccurrences((generated + obsolete).map { it.id })
    }

    /**
     * Changes the stored setup in place, without regenerating anything.
     *
     * For the "Advanced Features" switches, which take effect the moment they
     * are flipped rather than with "Einrichtung sichern": they lay down no task
     * and move no alarm, and a tab that only disappears after scrolling down to
     * a save button reads as a switch that does not work. [transform] is applied
     * to the **stored** row, so whatever else is half-edited in the settings'
     * draft is not saved along with it.
     */
    suspend fun update(transform: (UserSetup) -> UserSetup) {
        val stored = setupDao.find(SETUP_ID) ?: return
        setupDao.upsert(transform(stored).copy(id = SETUP_ID, updatedAt = clock.now()))
    }

    private suspend fun morningDefinitions(): List<Item> =
        itemDao.findByIdPrefix(SETUP_ITEM_ID_PREFIX)
            .filter { isMorningRoutine(it.id) && it.completedAt == null }
            .sortedBy { it.id }

    /**
     * The steps of the morning routine, as they stand.
     *
     * Every morning definition carries the same list, so the first one speaks
     * for all of them.
     */
    fun observeMorningSteps(): Flow<List<Subtask>> = combine(
        itemDao.observeRecurringDefinitions(),
        subtaskRepository.observeByItem(),
    ) { definitions, byItem ->
        definitions
            .filter { isMorningRoutine(it.id) }
            .sortedBy { it.id }
            .firstNotNullOfOrNull { byItem[it.id]?.takeIf { steps -> steps.isNotEmpty() } }
            .orEmpty()
            .sortedBy { it.position }
    }

    /**
     * Writes the morning routine's steps — onto **every** morning definition.
     *
     * With a weekend night there is one per night, and on the Listen tab the
     * weekdays and the weekend are two rows of the list; it is still one routine,
     * and steps that existed on Tuesday and not on Saturday would be a bug
     * nobody could find. Both places the steps are edited — the settings and the
     * standing task's own window — come through here.
     */
    suspend fun saveMorningSteps(drafts: List<SubtaskDraft>) {
        morningDefinitions().forEach { subtaskRepository.save(it.id, drafts) }
    }

    /**
     * Throws everything away, back to the state before the app was first opened.
     * Clearing the setup row is what sends the app to the questionnaire again —
     * the root screen derives its destination from it.
     *
     * For debugging. There is no undo and nothing is exported first.
     */
    suspend fun resetEverything() {
        resetDao.clearEverything()
    }
}
