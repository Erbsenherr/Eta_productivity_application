package com.example.eta.ui.growth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.GrowthService
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.RecurringEdit
import com.example.eta.data.repository.RecurringTaskService
import com.example.eta.data.repository.SubtaskRepository
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.recurrence.RecurringGroup
import com.example.eta.domain.recurrence.groupRecurring
import com.example.eta.ui.attributes.RecurringAttributes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GrowthTasksUiState(
    /** The growth tasks in their own order — one entry per task, not per weekday. */
    val groups: List<RecurringGroup> = emptyList(),
    /** Every live standing definition, which is what a fit check is asked against. */
    val definitions: List<Item> = emptyList(),
    /** The steps inside each task that has any, by item id. */
    val subtasks: Map<String, List<Subtask>> = emptyMap(),
    /** ToDos a group can swallow as steps. */
    val foldable: List<Item> = emptyList(),
)

/**
 * The Growth-Tasks tab.
 *
 * Two things live here that live nowhere else: **creating** a standing task from
 * a button — every other one in the app arrives from the questionnaire or from a
 * note the evening concretizes — and the **order** they hold, which is what
 * settles a slot two dynamic growth tasks both want.
 *
 * Everything else about a growth task is an ordinary standing task and is edited
 * through the same dialog the Listen tab uses.
 */
class GrowthTasksViewModel(
    private val growthService: GrowthService,
    private val recurringTaskService: RecurringTaskService,
    subtaskRepository: SubtaskRepository,
    private val itemRepository: ItemRepository,
) : ViewModel() {

    val uiState: StateFlow<GrowthTasksUiState> = combine(
        growthService.observeGrowthTasks(),
        recurringTaskService.observeDefinitions(),
        subtaskRepository.observeByItem(),
        itemRepository.observeFoldCandidates(),
    ) { growth, definitions, subtasks, foldable ->
        GrowthTasksUiState(
            subtasks = subtasks,
            foldable = foldable,
            // Grouped the same way the standing schedule is — a task on Tuesday
            // and Thursday is two rows in the database and one task to the user —
            // then put back into the order the tab was dragged into, which
            // grouping by attributes does not preserve.
            groups = groupRecurring(growth).sortedBy {
                it.representative.growthOrder ?: Int.MAX_VALUE
            },
            definitions = definitions,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GrowthTasksUiState(),
    )

    /**
     * Writes the order the list was dragged into.
     *
     * Groups, not rows: every definition of a task takes the same number, or the
     * Tuesday one would win its slot by a rule the Thursday one does not follow.
     */
    fun reorder(groups: List<RecurringGroup>) {
        viewModelScope.launch {
            growthService.reorder(groups.map { it.ids.toList() })
        }
    }

    /** A new growth task, last in the order. */
    fun create(name: String, note: String?, attributes: RecurringAttributes) {
        if (attributes.weekdays.isEmpty()) return
        viewModelScope.launch {
            recurringTaskService.createGroup(
                edit = RecurringEdit(
                    name = name,
                    note = note,
                    category = attributes.category,
                    weekdays = attributes.weekdays,
                    startTime = attributes.startTime,
                    duration = attributes.duration,
                    travelBefore = attributes.travelBefore,
                    returnAfter = attributes.returnAfter,
                    breakAfter = attributes.breakAfter,
                    endSound = attributes.endSound,
                    extras = attributes.extras,
                    growth = attributes.growth,
                    subtasks = attributes.subtasks,
                    folded = attributes.foldedItemIds,
                ),
                growthOrder = growthService.nextOrder(),
            )
        }
    }

    /**
     * Saves an edited growth task — the same call the Listen tab makes, so the
     * confirmed-days rule is obeyed here too.
     *
     * Switching growth off is allowed and is not a special case: the task stays,
     * at the length growing brought it to, and simply stops growing.
     */
    fun save(group: RecurringGroup, name: String, note: String?, attributes: RecurringAttributes) {
        if (attributes.weekdays.isEmpty()) return
        viewModelScope.launch {
            recurringTaskService.saveGroup(
                ids = group.ids,
                edit = RecurringEdit(
                    name = name,
                    note = note,
                    category = attributes.category,
                    weekdays = attributes.weekdays,
                    startTime = attributes.startTime,
                    duration = attributes.duration,
                    travelBefore = attributes.travelBefore,
                    returnAfter = attributes.returnAfter,
                    breakAfter = attributes.breakAfter,
                    endSound = attributes.endSound,
                    extras = attributes.extras,
                    growth = attributes.growth,
                    subtasks = attributes.subtasks,
                    folded = attributes.foldedItemIds,
                ),
            )
        }
    }

    /** Ends a growth task; what it already did stays in the Erfolgsliste. */
    fun end(group: RecurringGroup) {
        viewModelScope.launch { recurringTaskService.endGroup(group.ids) }
    }
}
