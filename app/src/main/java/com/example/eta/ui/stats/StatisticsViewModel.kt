package com.example.eta.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.NightService
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.RecurringTaskService
import com.example.eta.data.repository.SetupRepository
import com.example.eta.data.repository.SleepLogRepository
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.NightOverride
import com.example.eta.domain.model.SleepLog
import com.example.eta.domain.setup.UserSetup
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Everything the Statistiken tab counts from, read once.
 *
 * The figures themselves are worked out by the screen, per range — there are
 * several range pickers on it, one for the overview and one inside every task,
 * and each only cuts this same history differently.
 */
data class StatisticsData(
    val today: LocalDate,
    /** Every block there is up to today, whatever became of it. */
    val blocks: List<BlockWithItem> = emptyList(),
    val logs: List<SleepLog> = emptyList(),
    /** The live standing tasks. */
    val definitions: List<Item> = emptyList(),
    val setup: UserSetup? = null,
    val overrides: Map<LocalDate, NightOverride> = emptyMap(),
    val loaded: Boolean = false,
)

/**
 * The Statistiken tab.
 *
 * It writes nothing. The history is read **once per visit** rather than
 * observed: it is every block the database holds, and a statistic does not need
 * to move while it is being looked at.
 */
class StatisticsViewModel(
    private val planRepository: PlanRepository,
    private val sleepLogRepository: SleepLogRepository,
    recurringTaskService: RecurringTaskService,
    setupRepository: SetupRepository,
    nightService: NightService,
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val today: LocalDate = clock.todayIn(timeZone)

    private val history = MutableStateFlow<Pair<List<BlockWithItem>, List<SleepLog>>?>(null)

    val data: StateFlow<StatisticsData> = combine(
        history,
        recurringTaskService.observeDefinitions(),
        setupRepository.observe(),
        nightService.observeOverrides(),
    ) { history, definitions, setup, overrides ->
        StatisticsData(
            today = today,
            blocks = history?.first.orEmpty(),
            logs = history?.second.orEmpty(),
            definitions = definitions,
            setup = setup,
            overrides = overrides,
            loaded = history != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsData(today = today))

    init {
        refresh()
    }

    /** Reads the history again — on every visit to the tab. */
    fun refresh() {
        viewModelScope.launch {
            history.value = planRepository.findRange(EARLIEST, today) to
                sleepLogRepository.findRange(EARLIEST, today)
        }
    }

    private companion object {
        /** Before anything this app can hold; "seit Beginn" starts where the data does. */
        val EARLIEST = LocalDate(2000, 1, 1)
    }
}
