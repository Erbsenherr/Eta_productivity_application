package com.example.erik_iteration_2.data.repository

import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.data.local.JournalDao
import com.example.erik_iteration_2.data.local.PlannedBlockDao
import com.example.erik_iteration_2.domain.contract.contractsToCheck
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.JournalEntry
import com.example.erik_iteration_2.domain.model.JournalKind
import com.example.erik_iteration_2.domain.model.PointsReason
import com.example.erik_iteration_2.domain.planning.MINUTES_PER_DAY
import com.example.erik_iteration_2.domain.reevaluation.ContractVerdict
import com.example.erik_iteration_2.domain.reevaluation.DailySettlement
import com.example.erik_iteration_2.domain.reevaluation.settleDay
import com.example.erik_iteration_2.domain.setup.sleepDuration
import kotlin.time.Clock
import kotlinx.datetime.LocalDate

/**
 * The evening reevaluation, as far as it touches more than one table.
 *
 * This is the only place that writes HARVEST rows: the concept keeps the reward
 * out of the dashboard's checkbox on purpose, so that checking something off and
 * being paid for it stay separate acts.
 */
class ReevaluationService(
    private val blockDao: PlannedBlockDao,
    private val journalDao: JournalDao,
    private val contractRepository: ContractRepository,
    private val itemRepository: ItemRepository,
    private val planRepository: PlanRepository,
    private val pointsRepository: PointsRepository,
    private val setupRepository: SetupRepository,
    private val clock: Clock = Clock.System,
) {

    /** Contracts that still owe an answer for [date]. */
    suspend fun contractsAwaitingCheck(date: LocalDate): List<Contract> =
        contractsToCheck(contractRepository.findRunning(), date)

    /** How much of [date] the night took, so unplanned time is only charged awake. */
    private suspend fun sleepMinutes(): Int =
        setupRepository.find()?.sleepDuration()?.inWholeMinutes?.toInt()?.coerceIn(0, MINUTES_PER_DAY)
            ?: 0

    /** The daily free time the setup provides for — the ceiling on what a forfeit pays. */
    private suspend fun freeTimeAllowanceMinutes(): Int =
        setupRepository.find()?.freeTime?.duration?.inWholeMinutes?.toInt()?.coerceAtLeast(0) ?: 0

    /**
     * What the day would settle at, without writing anything.
     *
     * The reevaluation shows this to the user before booking it — "Belohnung!" is
     * a step of its own in the concept, not a silent side effect.
     */
    suspend fun preview(date: LocalDate, verdicts: List<ContractVerdict>): DailySettlement =
        settleDay(
            blocks = blockDao.findForDateWithItems(date),
            verdicts = verdicts,
            sleepMinutes = sleepMinutes(),
            freeTimeAllowanceMinutes = freeTimeAllowanceMinutes(),
        )

    /**
     * Books the day: contract verdicts, then every points movement as its own
     * ledger row.
     *
     * Separate rows rather than one net figure, because the balance is a ledger —
     * a month later it still has to be possible to see what the harvest was and
     * what the unplanned hours cost.
     */
    suspend fun settle(
        date: LocalDate,
        verdicts: List<ContractVerdict>,
        journal: Map<String, String>,
    ): DailySettlement {
        verdicts.forEach { contractRepository.recordVerdict(it.contract, it.kept, date) }

        val settlement = preview(date, verdicts)

        if (settlement.harvest != 0.0) {
            pointsRepository.record(settlement.harvest, PointsReason.HARVEST, note = date.toString())
        }
        if (settlement.contractPayout != 0.0) {
            pointsRepository.record(settlement.contractPayout, PointsReason.CONTRACT)
        }
        if (settlement.legacyCredited != 0.0) {
            pointsRepository.record(
                amount = settlement.legacyCredited,
                reason = PointsReason.LEGACY_CONTRACT,
                // What the cap swallowed is worth recording; it is the crown's reason.
                note = if (settlement.crowned) "gedeckelt von ${settlement.legacyGross}" else null,
            )
        }
        if (settlement.freeTimeForgone != 0.0) {
            pointsRepository.record(settlement.freeTimeForgone, PointsReason.FREE_TIME_FORGONE)
        }
        if (settlement.unplannedPenalty != 0.0) {
            pointsRepository.record(
                amount = -settlement.unplannedPenalty,
                reason = PointsReason.UNPLANNED_TIME,
                note = "${settlement.unplannedHours} h unverplant",
            )
        }

        val answered = journal.filterValues { it.isNotBlank() }
        if (answered.isNotEmpty()) {
            val now = clock.now()
            journalDao.insertAll(
                answered.map { (question, answer) ->
                    JournalEntry(
                        kind = JournalKind.DAILY,
                        date = date,
                        question = question,
                        answer = answer,
                        createdAt = now,
                    )
                },
            )
        }

        // A finished one-shot ToDo leaves the active lists here — nothing else
        // ever set Stage.DONE, and the week-goal gate has to see it as done.
        itemRepository.retireCompletedTodos(date)

        // The day is now accounted for. Without this mark the alarm would read a
        // planned-ahead tomorrow as "phase done" and never ask again.
        planRepository.markSettled(date)

        return settlement
    }

    suspend fun blocksOf(date: LocalDate): List<BlockWithItem> =
        blockDao.findForDateWithItems(date)
}
