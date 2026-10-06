package com.example.eta.ui.vacation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.VacationRepository
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Vacation
import com.example.eta.domain.model.VacationRule
import com.example.eta.domain.model.VacationTreatment
import com.example.eta.domain.vacation.VacationPlan
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** One recurring task and what the holiday being drafted does to it. */
data class DraftRule(
    val item: Item,
    val treatment: VacationTreatment?,
    val movedStart: LocalTime?,
)

data class VacationUiState(
    val today: LocalDate,
    val existing: List<VacationPlan> = emptyList(),
    val recurring: List<Item> = emptyList(),
)

class VacationViewModel(
    private val vacationRepository: VacationRepository,
    itemRepository: ItemRepository,
    private val clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val today: LocalDate = clock.todayIn(timeZone)

    private val _label = MutableStateFlow("Urlaub")
    val label: StateFlow<String> = _label.asStateFlow()

    private val _startsInDays = MutableStateFlow(0)
    val startsInDays: StateFlow<Int> = _startsInDays.asStateFlow()

    private val _lengthInDays = MutableStateFlow(7)
    val lengthInDays: StateFlow<Int> = _lengthInDays.asStateFlow()

    /** Keyed by item id; a task with no entry keeps happening as usual. */
    private val _decisions = MutableStateFlow<Map<String, DraftRule>>(emptyMap())
    val decisions: StateFlow<Map<String, DraftRule>> = _decisions.asStateFlow()

    val uiState: StateFlow<VacationUiState> = combine(
        vacationRepository.observePlans(),
        itemRepository.observeRecurringDefinitions(),
    ) { plans, recurring ->
        VacationUiState(today = today, existing = plans, recurring = recurring)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VacationUiState(today = today),
    )

    val from: LocalDate get() = today.plus(DatePeriod(days = _startsInDays.value))

    val to: LocalDate
        get() = from.plus(DatePeriod(days = (_lengthInDays.value - 1).coerceAtLeast(0)))

    fun setLabel(value: String) {
        _label.value = value
    }

    fun setStartsInDays(value: Int) {
        _startsInDays.value = value.coerceAtLeast(0)
    }

    fun setLengthInDays(value: Int) {
        _lengthInDays.value = value.coerceAtLeast(1)
    }

    /** Suspend, move, or back to normal when the same choice is tapped again. */
    fun choose(item: Item, treatment: VacationTreatment?) {
        _decisions.update { current ->
            if (treatment == null) {
                current - item.id
            } else {
                current + (
                    item.id to DraftRule(
                        item = item,
                        treatment = treatment,
                        // Its usual time is the sensible thing to start moving from.
                        movedStart = current[item.id]?.movedStart ?: item.startTime,
                    )
                    )
            }
        }
    }

    fun setMovedStart(item: Item, start: LocalTime) {
        _decisions.update { current ->
            val existing = current[item.id] ?: return@update current
            current + (item.id to existing.copy(movedStart = start))
        }
    }

    fun save(onSaved: () -> Unit) {
        val decided = _decisions.value.values.filter { it.treatment != null }
        if (decided.isEmpty()) return

        viewModelScope.launch {
            val now = clock.now()
            val vacation = Vacation(
                label = _label.value.ifBlank { "Urlaub" },
                from = from,
                to = to,
                createdAt = now,
                updatedAt = now,
            )
            vacationRepository.save(
                vacation = vacation,
                rules = decided.map {
                    VacationRule(
                        vacationId = vacation.id,
                        itemId = it.item.id,
                        treatment = it.treatment!!,
                        movedStart = it.movedStart,
                    )
                },
            )
            _decisions.value = emptyMap()
            onSaved()
        }
    }

    fun delete(plan: VacationPlan) {
        viewModelScope.launch { vacationRepository.delete(plan) }
    }
}
