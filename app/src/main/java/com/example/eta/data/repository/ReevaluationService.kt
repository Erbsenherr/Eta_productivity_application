package com.example.eta.data.repository

import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.local.ItemDao
import com.example.eta.data.local.JournalDao
import com.example.eta.data.local.PlannedBlockDao
import com.example.eta.domain.contract.contractsToCheck
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.Stage
import com.example.eta.domain.recurrence.matches
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.JournalEntry
import com.example.eta.domain.model.JournalKind
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.reevaluation.ContractVerdict
import com.example.eta.domain.reevaluation.DailySettlement
import com.example.eta.domain.reevaluation.settleDay
import com.example.eta.domain.reward.yieldOf
import com.example.eta.domain.setup.DEFAULT_CANCELLATION_PENALTY
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.planning.sleepStretches
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
    private val itemDao: ItemDao,
    private val blockDao: PlannedBlockDao,
    private val journalDao: JournalDao,
    private val contractRepository: ContractRepository,
    private val itemRepository: ItemRepository,
    private val planRepository: PlanRepository,
    private val pointsRepository: PointsRepository,
    private val setupRepository: SetupRepository,
    private val growthService: GrowthService,
    private val clock: Clock = Clock.System,
) {

    /** Contracts that still owe an answer for [date]. */
    suspend fun contractsAwaitingCheck(date: LocalDate): List<Contract> =
        contractsToCheck(contractRepository.findRunning(), date)

    /** How much of [date] the night took, so unplanned time is only charged awake. */
    private fun sleepMinutes(setup: UserSetup?, date: LocalDate): Int =
        // The night as it falls on this calendar day — the same stretches the
        // planner shades — so a weekend night, or one that begins after
        // midnight, is counted on the day it actually takes hours from.
        setup?.sleepStretches(date.dayOfWeek)
            ?.sumOf { it.last - it.first + 1 }
            ?.coerceIn(0, MINUTES_PER_DAY) ?: 0

    /**
     * The daily free time the standing schedule provides for on [date] — the
     * ceiling on what a forfeit pays.
     *
     * Read off the `FREE_TIME` definitions rather than the questionnaire's answer.
     * Free time is a standing task now, edited on the Listen tab like any other,
     * and a cap still reading the answer from the first day would let the two
     * drift apart the first time it was changed.
     */
    private suspend fun freeTimeAllowanceMinutes(date: LocalDate): Int =
        itemDao.findRecurringDefinitions()
            .filter { it.role == ItemRole.FREE_TIME && it.stage != Stage.COLLECTION }
            .filter { it.recurrenceRule?.matches(date) == true }
            .sumOf { it.estimatedDuration?.inWholeMinutes?.toInt() ?: 0 }
            .coerceAtLeast(0)

    /**
     * What the day would settle at, without writing anything.
     *
     * The reevaluation shows this to the user before booking it — "Belohnung!" is
     * a step of its own in the concept, not a silent side effect.
     */
    suspend fun preview(date: LocalDate, verdicts: List<ContractVerdict>): DailySettlement {
        val setup = setupRepository.find()
        return settleDay(
            blocks = blockDao.findForDateWithItems(date),
            verdicts = verdicts,
            sleepMinutes = sleepMinutes(setup, date),
            freeTimeAllowanceMinutes = freeTimeAllowanceMinutes(date),
            // What a called-off hour costs, from the standing configuration.
            cancellationPenaltyPerHour = setup?.cancellationPenaltyPerHour
                ?: DEFAULT_CANCELLATION_PENALTY,
        )
    }

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
        verdicts.forEach {
            contractRepository.recordVerdict(it.contract, it.kept, date, it.keepServing)
        }

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
        if (settlement.cancellationPenalty != 0.0) {
            pointsRepository.record(
                amount = -settlement.cancellationPenalty,
                reason = PointsReason.CANCELLATION,
                note = "${settlement.cancelledHours} h abgesagt",
            )
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

        // A growth task grows one step for every completion the evening confirms,
        // which is this moment and no other: the same reasoning that keeps the
        // harvest out of the dashboard's tick. Before `markSettled`, because that
        // mark is what stops a second run from growing the same task twice.
        growthService.advanceFor(date)

        // The day is now accounted for. Without this mark the alarm would read a
        // planned-ahead tomorrow as "phase done" and never ask again.
        planRepository.markSettled(date)

        return settlement
    }

    /**
     * Books one completion that arrived after its own day had been settled.
     *
     * The harvest belongs to the evening, and normally that is enough: something
     * ticked off during the day is paid for when the day is closed. A card
     * finished *after* that — the Listen tab's "Erledigt", used late — would
     * otherwise earn nothing at all and say nothing about why, which is the one
     * way the reward can silently go missing.
     *
     * Here rather than in the view model that calls it, so the sentence in this
     * class's own header stays true: HARVEST is written in one place. The note
     * marks it, because a ledger read a month later has to be able to tell a
     * day's harvest from something added to it afterwards.
     *
     * Returns what it booked, or zero if the day is still open — in which case
     * the ordinary settlement will pick the block up.
     */
    suspend fun harvestLate(entry: BlockWithItem): Double {
        val date = entry.block.date
        if (planRepository.findDayPlan(date)?.isSettled != true) return 0.0
        val amount = yieldOf(entry.item, entry.block)
        if (amount != 0.0) {
            pointsRepository.record(amount, PointsReason.HARVEST, note = "$date nachgetragen")
        }
        return amount
    }

    suspend fun blocksOf(date: LocalDate): List<BlockWithItem> =
        blockDao.findForDateWithItems(date)
}
