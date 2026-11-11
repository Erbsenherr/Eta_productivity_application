package com.example.eta.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.RuleDraft
import com.example.eta.data.repository.RuleRepository
import com.example.eta.domain.model.Rule
import com.example.eta.domain.model.RuleBreach
import com.example.eta.domain.rules.ActiveConsequence
import com.example.eta.domain.rules.consequencesOn
import com.example.eta.domain.rules.freeTimeConsequenceDays
import com.example.eta.domain.rules.rulesToWatch
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** What the rest of the app reads off the Regelwerk (step 57). */
data class RulebookState(
    val rules: List<Rule> = emptyList(),
    /** Today's breaches, with their rules — the dashboard's red box. */
    val today: List<ActiveConsequence> = emptyList(),
    /** The days whose free time is hatched red. */
    val freeTimeDays: Set<LocalDate> = emptySet(),
    /** Rules broken in the last two weeks — the morning's reminder. */
    val toWatch: List<Rule> = emptyList(),
)

/**
 * The Regelwerk: rules written down, changed and deleted (step 53) — and, since
 * step 57, reported broken, with what that brings on the day.
 *
 * Built once at the root for the "!" button and the hatching, and once by the
 * tab; both read the same tables.
 */
class RulesViewModel(
    private val ruleRepository: RuleRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val rules: StateFlow<List<Rule>> = ruleRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<RulebookState> = combine(
        ruleRepository.observe(),
        ruleRepository.observeBreaches(),
    ) { rules, breaches -> stateOf(rules, breaches) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulebookState())

    private fun stateOf(rules: List<Rule>, breaches: List<RuleBreach>): RulebookState {
        // Read each time: a view model kept overnight must not stay on yesterday.
        val today = clock.todayIn(timeZone)
        return RulebookState(
            rules = rules,
            today = consequencesOn(today, breaches, rules),
            freeTimeDays = freeTimeConsequenceDays(breaches, rules),
            toWatch = rulesToWatch(today, breaches, rules),
        )
    }

    /** A new rule for a null [id], otherwise the one it names. */
    fun save(id: String?, draft: RuleDraft) {
        viewModelScope.launch { ruleRepository.save(id, draft) }
    }

    fun delete(id: String) {
        viewModelScope.launch { ruleRepository.delete(id) }
    }

    /** "Regelverstoß melden": broken today. */
    fun report(ruleId: String) {
        viewModelScope.launch { ruleRepository.report(ruleId, clock.todayIn(timeZone)) }
    }

    /** A report made by mistake, taken back from the dashboard. */
    fun withdraw(breachId: String) {
        viewModelScope.launch { ruleRepository.withdraw(breachId) }
    }
}
