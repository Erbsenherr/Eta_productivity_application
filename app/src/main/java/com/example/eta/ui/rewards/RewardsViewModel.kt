package com.example.eta.ui.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.RecurringTaskService
import com.example.eta.data.repository.RewardRepository
import com.example.eta.data.repository.RewardWithTasks
import com.example.eta.domain.model.Item
import com.example.eta.domain.recurrence.RecurringGroup
import com.example.eta.domain.recurrence.groupRecurring
import com.example.eta.ui.attributes.RecurringAttributes
import com.example.eta.ui.attributes.toEdit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One reward as the tab draws it. */
data class RewardRow(
    val entry: RewardWithTasks,
    /** The standing tasks it is bound to, by name. Empty means every task counts. */
    val boundTo: List<String>,
    /** The first one still filling: the only one the evening pours into. */
    val isHead: Boolean,
) {
    val reward get() = entry.reward
}

data class RewardsUiState(
    /** Everything not yet taken, in list order — full ones included. */
    val open: List<RewardRow> = emptyList(),
    /** What has been taken, newest first. */
    val redeemed: List<RewardRow> = emptyList(),
    /** The standing schedule, one entry per task: what a reward can be bound to. */
    val groups: List<RecurringGroup> = emptyList(),
    /** Every live standing definition, for the overlap check of a new task. */
    val definitions: List<Item> = emptyList(),
)

/**
 * The Belohn-o-mat tab.
 *
 * Nothing here fills a reward — that is the evening's, through
 * `ReevaluationService`. This screen makes them, orders them, binds them to
 * standing tasks and takes the ones that have been earned.
 */
class RewardsViewModel(
    private val rewardRepository: RewardRepository,
    private val recurringTaskService: RecurringTaskService,
) : ViewModel() {

    val uiState: StateFlow<RewardsUiState> = combine(
        rewardRepository.observe(),
        recurringTaskService.observeDefinitions(),
    ) { rewards, definitions ->
        val groups = groupRecurring(definitions)
        val headId = rewards.firstOrNull { it.reward.isFilling }?.reward?.id
        val rows = rewards.map { entry ->
            RewardRow(
                entry = entry,
                boundTo = groups.filter { it.isBoundBy(entry.boundNames) }
                    .map { it.representative.name }
                    .distinct(),
                isHead = entry.reward.id == headId,
            )
        }
        RewardsUiState(
            open = rows.filter { !it.reward.isRedeemed },
            redeemed = rows.filter { it.reward.isRedeemed }
                .sortedByDescending { it.reward.redeemedAt },
            groups = groups,
            definitions = definitions,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RewardsUiState(),
    )

    /** Creates a reward, or — with an [id] — changes the one that exists. */
    fun save(id: String?, name: String, cost: Int, itemIds: Set<String>) {
        viewModelScope.launch {
            if (id == null) {
                rewardRepository.create(name, cost.toDouble(), itemIds)
            } else {
                rewardRepository.update(id, name, cost.toDouble(), itemIds)
            }
        }
    }

    fun reorder(ids: List<String>) {
        viewModelScope.launch { rewardRepository.reorder(ids) }
    }

    fun redeem(id: String) {
        viewModelScope.launch { rewardRepository.redeem(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { rewardRepository.delete(id) }
    }

    /**
     * A standing task made from the binding menu.
     *
     * [onCreated] receives the ids of the rows written, so the menu can come back
     * with the new task already ticked — it was made in order to be bound.
     */
    fun createTask(
        name: String,
        note: String?,
        attributes: RecurringAttributes,
        onCreated: (List<String>) -> Unit,
    ) {
        if (attributes.weekdays.isEmpty()) return
        viewModelScope.launch {
            onCreated(recurringTaskService.createGroup(attributes.toEdit(name, note)))
        }
    }
}

/**
 * Whether a binding to [names] means this task — by name, which is how the
 * evening reads it too. See `RewardRepository.boundNames`.
 */
fun RecurringGroup.isBoundBy(names: Set<String>): Boolean =
    representative.normalizedName in names
