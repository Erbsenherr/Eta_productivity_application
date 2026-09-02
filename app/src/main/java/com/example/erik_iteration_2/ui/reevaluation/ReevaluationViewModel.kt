package com.example.erik_iteration_2.ui.reevaluation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.data.repository.DayClosingService
import com.example.erik_iteration_2.data.repository.ItemRepository
import com.example.erik_iteration_2.data.repository.PlanRepository
import com.example.erik_iteration_2.data.repository.ReevaluationService
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.reevaluation.ContractVerdict
import com.example.erik_iteration_2.domain.reevaluation.DailySettlement
import com.example.erik_iteration_2.domain.reevaluation.DiscardConsequence
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

data class ReevaluationUiState(
    val date: LocalDate,
    val blocks: List<BlockWithItem> = emptyList(),
    val contracts: List<Contract> = emptyList(),
    val verdicts: Map<String, Boolean> = emptyMap(),
    val recurringDefinitions: List<Item> = emptyList(),
    val makeUpOffers: List<MakeUpOffer> = emptyList(),
    val preview: DailySettlement? = null,
    val settled: DailySettlement? = null,
    val loading: Boolean = true,
)

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
    private val clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /** The day being closed is today; the planner then plans tomorrow. */
    val date: LocalDate = clock.now().toLocalDateTime(timeZone).date

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
            _state.update {
                it.copy(
                    blocks = blocks,
                    contracts = contracts,
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
                    loading = false,
                )
            }
            recomputePreview()
        }
    }

    private fun recomputePreview() {
        viewModelScope.launch {
            val state = _state.value
            val verdicts = state.contracts.map {
                ContractVerdict(it, state.verdicts[it.id] ?: true)
            }
            _state.update { it.copy(preview = reevaluationService.preview(date, verdicts)) }
        }
    }

    fun toggleCompleted(entry: BlockWithItem) {
        viewModelScope.launch {
            if (entry.block.isCompleted) {
                planRepository.reopen(entry.block)
            } else {
                planRepository.complete(entry.block)
            }
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

    fun dismissMakeUp(offer: MakeUpOffer) {
        _state.update { state ->
            state.copy(makeUpOffers = state.makeUpOffers.filterNot { it.entry.block.id == offer.entry.block.id })
        }
    }

    fun setVerdict(contract: Contract, kept: Boolean) {
        _state.update { it.copy(verdicts = it.verdicts + (contract.id to kept)) }
        recomputePreview()
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
            val verdicts = state.contracts.map {
                ContractVerdict(it, state.verdicts[it.id] ?: true)
            }
            val settlement = reevaluationService.settle(date, verdicts, _journal.value)
            _state.update { it.copy(settled = settlement) }
            onDone()
        }
    }
}
