package com.example.erik_iteration_2.data.repository

import com.example.erik_iteration_2.data.local.ItemDao
import com.example.erik_iteration_2.data.local.ResetDao
import com.example.erik_iteration_2.data.local.SetupDao
import com.example.erik_iteration_2.domain.setup.SETUP_ID
import com.example.erik_iteration_2.domain.setup.SETUP_ITEM_ID_PREFIX
import com.example.erik_iteration_2.domain.setup.UserSetup
import com.example.erik_iteration_2.domain.setup.recurringItems
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
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
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    /** Null until the questionnaire has been answered — what gates the app's first screen. */
    fun observe(): Flow<UserSetup?> = setupDao.observe(SETUP_ID)

    suspend fun find(): UserSetup? = setupDao.find(SETUP_ID)

    /**
     * Stores the answers and brings the standing schedule in line with them.
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
