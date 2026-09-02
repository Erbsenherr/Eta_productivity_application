package com.example.erik_iteration_2.data.repository

import com.example.erik_iteration_2.data.local.PlannedBlockDao
import com.example.erik_iteration_2.data.local.VacationDao
import com.example.erik_iteration_2.domain.model.Vacation
import com.example.erik_iteration_2.domain.model.VacationRule
import com.example.erik_iteration_2.domain.model.VacationTreatment
import com.example.erik_iteration_2.domain.vacation.VacationPlan
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class VacationRepository(
    private val vacationDao: VacationDao,
    private val blockDao: PlannedBlockDao,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    fun observePlans(): Flow<List<VacationPlan>> =
        combine(vacationDao.observeAll(), vacationDao.observeAllRules()) { vacations, rules ->
            vacations.map { vacation ->
                VacationPlan(vacation, rules.filter { it.vacationId == vacation.id })
            }
        }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZone).date

    /** The holiday covering [date], with its rules — what expansion consults. */
    suspend fun planFor(date: LocalDate): VacationPlan? {
        val vacation = vacationDao.findCovering(date) ?: return null
        return VacationPlan(vacation, vacationDao.findRules(vacation.id))
    }

    /**
     * Every holiday that has not finished by [date].
     *
     * What any materialization of a future range needs: a fortnight can easily
     * reach past the end of one holiday and into the next.
     */
    suspend fun plansFrom(date: LocalDate): List<VacationPlan> =
        vacationDao.findFrom(date).map { VacationPlan(it, vacationDao.findRules(it.id)) }

    /**
     * Saves a holiday and its per-task decisions, then brings the days it covers
     * into line.
     *
     * Bringing them into line is the part that is easy to forget: blocks already
     * materialized before the holiday was declared are still sitting on those
     * days, and expansion only decides what to *create*. Suspended ones are
     * removed, moved ones are shifted.
     */
    suspend fun save(vacation: Vacation, rules: List<VacationRule>) {
        val now = clock.now()
        vacationDao.upsert(vacation.copy(updatedAt = now))
        vacationDao.clearRules(vacation.id)
        vacationDao.upsertRules(rules.map { it.copy(vacationId = vacation.id) })
        applyTo(VacationPlan(vacation, rules))
    }

    /**
     * Ends a holiday and takes its effects with it.
     *
     * Only the blocks it changed are cleared; the next planning phase materializes
     * them again from the untouched definitions, which is why "Verschieben" never
     * had to edit `startTime` in the first place.
     */
    suspend fun delete(plan: VacationPlan) {
        vacationDao.clearRules(plan.vacation.id)
        vacationDao.delete(plan.vacation.id)
        clearFutureBlocks(plan, plan.rules.map { it.itemId }.toSet())
    }

    private suspend fun applyTo(plan: VacationPlan) {
        val suspended = plan.rules
            .filter { it.treatment == VacationTreatment.SUSPEND }
            .map { it.itemId }
            .toSet()
        val moved = plan.rules.filter {
            it.treatment == VacationTreatment.MOVE && it.movedStart != null
        }

        clearFutureBlocks(plan, suspended)

        if (moved.isEmpty()) return
        val now = clock.now()
        val byItem = moved.associateBy { it.itemId }
        val affected = blockDao
            .findForRangeWithItems(startOfEffect(plan), plan.vacation.to)
            .filter { it.item.id in byItem.keys }
            .mapNotNull { entry ->
                byItem[entry.item.id]?.movedStart?.let { start ->
                    entry.block.copy(start = start, updatedAt = now)
                }
            }
        if (affected.isNotEmpty()) blockDao.upsertAll(affected)
    }

    private suspend fun clearFutureBlocks(plan: VacationPlan, itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        val doomed = blockDao
            .findForRangeWithItems(startOfEffect(plan), plan.vacation.to)
            .filter { it.item.id in itemIds }
        doomed.forEach { blockDao.delete(it.block) }
    }

    /**
     * A holiday never rewrites days that are already over: what happened,
     * happened, and the Erfolgsliste says so.
     */
    private fun startOfEffect(plan: VacationPlan): LocalDate =
        maxOf(plan.vacation.from, today())
}
