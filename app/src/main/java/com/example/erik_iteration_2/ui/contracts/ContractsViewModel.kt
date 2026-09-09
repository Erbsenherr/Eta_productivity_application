package com.example.erik_iteration_2.ui.contracts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erik_iteration_2.data.repository.ContractRepository
import com.example.erik_iteration_2.data.repository.SignResult
import com.example.erik_iteration_2.domain.contract.SlotStatus
import com.example.erik_iteration_2.domain.contract.slotStatuses
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.ContractEffort
import com.example.erik_iteration_2.domain.model.ContractState
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class ContractsUiState(
    val today: LocalDate,
    val slots: List<SlotStatus> = emptyList(),
    val legacy: List<Contract> = emptyList(),
    /** Ran their term and are waiting to be extended, changed or ended. */
    val expiring: List<Contract> = emptyList(),
    /** Have run a month and may be upgraded, freeing their slot. */
    val upgradable: List<Contract> = emptyList(),
    val closed: List<Contract> = emptyList(),
)

class ContractsViewModel(
    private val contractRepository: ContractRepository,
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val today: LocalDate = clock.now().toLocalDateTime(timeZone).date

    private val _refusal = MutableStateFlow<String?>(null)
    val refusal: StateFlow<String?> = _refusal.asStateFlow()

    val uiState: StateFlow<ContractsUiState> = contractRepository.observeAll()
        .map { contracts ->
            ContractsUiState(
                today = today,
                slots = slotStatuses(contracts, today),
                legacy = contracts.filter { it.state == ContractState.LEGACY },
                expiring = contracts.filter { it.isExpiring(today) },
                upgradable = contracts.filter { it.canUpgradeToLegacy(today) },
                closed = contracts.filter {
                    it.state == ContractState.BROKEN || it.state == ContractState.FULFILLED
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ContractsUiState(today = today),
        )

    /** Signs, or surfaces the reason it cannot be signed. */
    fun sign(
        slot: Int,
        title: String,
        conditions: String,
        breachDefinition: String,
        effort: ContractEffort,
        signature: String,
        weeks: Int,
        onSigned: () -> Unit,
    ) {
        viewModelScope.launch {
            val result = contractRepository.sign(
                slot = slot,
                title = title,
                conditions = conditions,
                breachDefinition = breachDefinition,
                effort = effort,
                signature = signature,
                endsOn = today.plus(DatePeriod(days = weeks * 7)),
            )
            when (result) {
                is SignResult.Signed -> onSigned()
                is SignResult.Refused -> _refusal.value = result.reason
            }
        }
    }

    /** Changing a contract's wording; the dialog has already stated what it costs. */
    fun editWording(contract: Contract, title: String, conditions: String, breach: String) {
        viewModelScope.launch {
            contractRepository.editWording(contract, title, conditions, breach)
        }
    }

    fun upgradeToLegacy(contract: Contract) {
        viewModelScope.launch { contractRepository.upgradeToLegacy(contract) }
    }

    /** Extending a contract that ran its term, by the same length again. */
    fun extend(contract: Contract, weeks: Int) {
        viewModelScope.launch {
            contractRepository.extend(contract, today.plus(DatePeriod(days = weeks * 7)))
        }
    }

    fun fulfil(contract: Contract) {
        viewModelScope.launch { contractRepository.fulfil(contract) }
    }

    fun dismissRefusal() {
        _refusal.value = null
    }
}
