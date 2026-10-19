package com.example.eta.ui.reevaluation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.DayClosingService
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.SubtaskRepository
import com.example.eta.data.repository.ReevaluationService
import com.example.eta.data.repository.RewardOutlook
import com.example.eta.domain.growth.timerBufferOrZero
import com.example.eta.domain.model.Contract
import com.example.eta.domain.planning.fullTimerGoal
import com.example.eta.domain.planning.timerBilled
import com.example.eta.domain.planning.timerPaused
import com.example.eta.domain.planning.timerWasUsed
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.reevaluation.ContractVerdict
import com.example.eta.domain.reevaluation.DailySettlement
import com.example.eta.domain.reevaluation.DiscardConsequence
import com.example.eta.domain.subtask.stillOpen
import com.example.eta.domain.streak.StreakOutcome
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** The self-reflection questions, in the order `Tägliche Reevaluation.md` asks them. */
object JournalQuestions {
    const val WENT_WELL = "Was lief gut an dem Tag?"
    const val MISSING_RECURRING =
        "Ist dir eine wiederkehrende Aufgabe aufgefallen, die der Plan noch nicht berücksichtigt?"
    const val STRESS = "War der Tag stressig?"
    const val STRESS_FROM_PLAN = "War der Tag stressig wegen deines Plans?"
    const val PLAN_CHANGE = "Was könntest du am Plan anpassen?"
}

enum class StressLevel(val label: String) {
    SEHR("Sehr"),
    ETWAS("Etwas"),
    NICHT("Nicht"),
}

/** A recurring occurrence that was dropped and could be caught up. */
data class MakeUpOffer(
    val entry: BlockWithItem,
    val createdItem: Item? = null,
)

/**
 * A group whose day is over with steps still open.
 *
 * Read off the blocks rather than remembered from a gesture, so ticking the last
 * step off in the evening makes the question go away by itself. It is put for a
 * **finished** group as well as a dropped one: the task was called done, but three
 * of its four steps were not, and those three are what is worth carrying over.
 */
data class RemainderOffer(
    val entry: BlockWithItem,
    val open: List<Subtask>,
    val total: Int,
    val createdItem: Item? = null,
)

data class ReevaluationUiState(
    val date: LocalDate,
    val blocks: List<BlockWithItem> = emptyList(),
    val contracts: List<Contract> = emptyList(),
    val verdicts: Map<String, Boolean> = emptyMap(),
    /**
     * For a breach, whether the promise is taken up again anyway.
     *
     * Its own map rather than a third verdict value, because it answers a second
     * question that only exists once the first has been answered with "no" — and
     * letting the contract go is what happens if it is never asked.
     */
    val continuations: Map<String, Boolean> = emptyMap(),
    val recurringDefinitions: List<Item> = emptyList(),
    val makeUpOffers: List<MakeUpOffer> = emptyList(),
    /** Groups with steps still open, once their block has been answered. */
    val remainders: List<RemainderOffer> = emptyList(),
    val preview: DailySettlement? = null,
    /**
     * What tonight would pour into the Belohn-o-mat. Null until it is known, and
     * a null head inside it means there is no reward to fill — and no page.
     */
    val rewards: RewardOutlook? = null,
    /**
     * Step 48: what tonight does to every streak, the series first. Empty while
     * the feature is off — and then the page is not shown.
     */
    val streaks: List<StreakOutcome> = emptyList(),
    /** Broken streaks the user wants to start again, by id; unanswered ones are let go. */
    val restarts: Map<String, Boolean> = emptyMap(),
    val settled: DailySettlement? = null,
    val loading: Boolean = true,
) {
    /**
     * The answers as the settlement and the repository want them.
     *
     * One place rather than three: the preview, the booking and anything that
     * comes later all have to read the two maps the same way, and the defaults —
     * kept unless said otherwise, let go unless said otherwise — are the whole
     * meaning of a question left untouched.
     */
    fun toVerdicts(): List<ContractVerdict> = contracts.map { contract ->
        ContractVerdict(
            contract = contract,
            kept = verdicts[contract.id] ?: true,
            keepServing = continuations[contract.id] ?: false,
        )
    }
}

/**
 * The evening reevaluation.
 *
 * Its steps are worked through in order and only the last one writes: everything
 * before it can be revised, which is why the reward is previewed rather than
 * booked as each answer comes in.
 */
class ReevaluationViewModel(
    private val reevaluationService: ReevaluationService,
    private val planRepository: PlanRepository,
    private val itemRepository: ItemRepository,
    private val dayClosingService: DayClosingService,
    private val subtaskRepository: SubtaskRepository,
    private val clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /** The day being closed is today; the planner then plans tomorrow. */
    val date: LocalDate = clock.todayIn(timeZone)

    private val _state = MutableStateFlow(ReevaluationUiState(date = date))
    val uiState: StateFlow<ReevaluationUiState> = _state.asStateFlow()

    private val _journal = MutableStateFlow<Map<String, String>>(emptyMap())
    val journal: StateFlow<Map<String, String>> = _journal.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val blocks = reevaluationService.blocksOf(date)
            val contracts = reevaluationService.contractsAwaitingCheck(date)
            val steps = subtaskRepository.stepsByItem()
            val ticks = subtaskRepository.checkedByBlock(date)
            // A block that has been answered — done or dropped — with steps still
            // unticked. Derived here rather than latched when it happened, so the
            // offer disappears the moment the last step is ticked off.
            val remainders = blocks.mapNotNull { entry ->
                val all = steps[entry.item.id].orEmpty()
                if (all.isEmpty() || entry.block.isOpen) return@mapNotNull null
                val open = all.stillOpen(ticks[entry.block.id].orEmpty())
                if (open.isEmpty()) return@mapNotNull null
                RemainderOffer(
                    entry = entry,
                    open = open,
                    total = all.size,
                    createdItem = entry.block.makeUpItemId?.let { id ->
                        itemRepository.findById(id)
                    },
                )
            }
            _state.update {
                it.copy(
                    blocks = blocks,
                    contracts = contracts,
                    remainders = remainders,
                    // Unanswered contracts count as kept until said otherwise: the
                    // question is "did you keep it", and a breach is the exception.
                    // Answers already given survive — this runs again after every
                    // tick and every drop, and silently un-breaking a contract the
                    // user just admitted to would be the worst kind of quiet.
                    verdicts = contracts.associate { contract ->
                        contract.id to (it.verdicts[contract.id] ?: true)
                    },
                    recurringDefinitions = blocks
                        .map { entry -> entry.item }
                        .filter { item -> item.recurrenceRule != null }
                        .distinctBy { item -> item.id },
                    // A group carries its rest over instead of being caught up
                    // whole — the same column holds both, so they cannot coexist.
                    makeUpOffers = it.makeUpOffers.filterNot { offer ->
                        remainders.any { rest -> rest.entry.block.id == offer.entry.block.id }
                    },
                    loading = false,
                )
            }
            recomputePreview()
        }
    }

    private fun recomputePreview() {
        viewModelScope.launch {
            val state = _state.value
            _state.update {
                it.copy(
                    preview = reevaluationService.preview(date, state.toVerdicts()),
                    // Off the blocks as well: a tick taken back changes a streak.
                    streaks = reevaluationService.previewStreaks(date),
                    // Off the same blocks, so a tick taken back on the first
                    // page takes its points back out of the bar as well.
                    rewards = reevaluationService.previewRewards(date),
                )
            }
        }
    }

    fun toggleCompleted(entry: BlockWithItem) {
        viewModelScope.launch {
            if (entry.block.isCompleted) {
                planRepository.reopen(entry.block)
            } else if (entry.item.timerTask && entry.block.timerWasUsed) {
                // A Timer-Task whose timer was started and never ended: what it
                // counted is what the sitting was. One never started is taken at
                // its word, and the stepper under the row corrects it.
                val now = clock.now()
                val block = entry.block
                planRepository.complete(
                    block.timerPaused(now),
                    actualDuration = block.timerBilled(now, entry.item.timerBufferOrZero)
                        .takeIf { it < block.plannedDuration },
                )
            } else {
                planRepository.complete(entry.block)
            }
            refresh()
        }
    }

    /**
     * A Timer-Task whose timer was never started, ticked in the evening after
     * the screen has asked. [worked] is the time the user says was put in, or
     * null for all of it — stored as the dashboard stores it, so the increment
     * and the yield are decided by the one rule.
     */
    fun completeTimerByHand(entry: BlockWithItem, worked: Duration?) {
        viewModelScope.launch {
            val block = entry.block
            val buffer = entry.item.timerBufferOrZero
            val billed = worked
                ?.takeIf { it < block.fullTimerGoal(buffer) }
                ?.let { (it + buffer).coerceAtMost(block.plannedDuration) }
            planRepository.complete(block.copy(actualDuration = null), actualDuration = billed)
            refresh()
        }
    }

    fun setActualDuration(entry: BlockWithItem, duration: Duration) {
        viewModelScope.launch {
            planRepository.addBlock(
                entry.block.copy(
                    actualDuration = duration.coerceAtLeast(Duration.ZERO),
                    updatedAt = clock.now(),
                ),
            )
            refresh()
        }
    }

    /**
     * Consciously dropping an occurrence. A missed recurring one is offered as a
     * catch-up rather than being turned into one silently — the user still holds
     * the commitment, but they decide whether to reschedule it.
     */
    fun discard(entry: BlockWithItem) {
        viewModelScope.launch {
            val consequence = dayClosingService.discard(entry)
            if (consequence == DiscardConsequence.OFFER_MAKE_UP) {
                _state.update { it.copy(makeUpOffers = it.makeUpOffers + MakeUpOffer(entry)) }
            }
            refresh()
        }
    }

    /**
     * Marks a cancellation as beyond the user's control.
     *
     * The settlement is recomputed from the blocks, so the line simply is not
     * there any more — nothing is refunded, because nothing was booked yet.
     */
    fun excuse(entry: BlockWithItem, reason: String) {
        viewModelScope.launch {
            planRepository.excuse(entry.block, reason)
            refresh()
        }
    }

    fun makeUp(offer: MakeUpOffer) {
        viewModelScope.launch {
            val created = dayClosingService.makeUp(offer.entry)
            _state.update { state ->
                state.copy(
                    makeUpOffers = state.makeUpOffers.map {
                        if (it.entry.block.id == offer.entry.block.id) it.copy(createdItem = created) else it
                    },
                )
            }
        }
    }

    /**
     * "Reste übernehmen": the open steps become a group of their own in the week
     * list, ready to be planned.
     */
    fun carryOver(offer: RemainderOffer) {
        viewModelScope.launch {
            dayClosingService.carryOverSubtasks(
                entry = offer.entry,
                open = offer.open,
                total = offer.total,
                today = date,
            )
            refresh()
        }
    }

    fun dismissRemainder(offer: RemainderOffer) {
        _state.update { state ->
            state.copy(
                remainders = state.remainders
                    .filterNot { it.entry.block.id == offer.entry.block.id },
            )
        }
    }

    fun dismissMakeUp(offer: MakeUpOffer) {
        _state.update { state ->
            state.copy(makeUpOffers = state.makeUpOffers.filterNot { it.entry.block.id == offer.entry.block.id })
        }
    }

    fun setVerdict(contract: Contract, kept: Boolean) {
        _state.update { it.copy(verdicts = it.verdicts + (contract.id to kept)) }
        recomputePreview()
    }

    /**
     * The follow-up to a breach: hold the promise anyway, or let it go.
     *
     * Neither pays — a broken contract is broken — so the preview does not move
     * and is not recomputed. What it decides is whether the slot goes on holding
     * a contract that is still asked about every evening.
     */
    fun setContinuation(contract: Contract, keepServing: Boolean) {
        _state.update { it.copy(continuations = it.continuations + (contract.id to keepServing)) }
    }

    /** A broken streak: start it again, or let it go. */
    fun setRestart(streakId: String, restart: Boolean) {
        _state.update { it.copy(restarts = it.restarts + (streakId to restart)) }
    }

    fun answer(question: String, answer: String) {
        _journal.update { it + (question to answer) }
    }

    /** Retiring a recurring definition the review found obsolete. */
    fun retireRecurring(item: Item) {
        viewModelScope.launch {
            itemRepository.update(item.copy(completedAt = clock.now()))
            refresh()
        }
    }

    /** The one step that writes: verdicts, ledger rows and journal, in one go. */
    fun settle(onDone: () -> Unit) {
        viewModelScope.launch {
            val state = _state.value
            if (state.settled != null) return@launch
            val settlement = reevaluationService.settle(
                date = date,
                verdicts = state.toVerdicts(),
                journal = _journal.value,
                restartStreaks = state.restarts.filterValues { it }.keys,
            )
            _state.update { it.copy(settled = settlement) }
            onDone()
        }
    }
}
