package com.example.eta.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.RuleRepository
import com.example.eta.domain.model.Rule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Regelwerk (step 53): rules written down, changed and deleted, nothing more. */
class RulesViewModel(private val ruleRepository: RuleRepository) : ViewModel() {

    val rules: StateFlow<List<Rule>> = ruleRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A new rule for a null [id], otherwise the one it names. */
    fun save(id: String?, question: String, answer: String) {
        viewModelScope.launch { ruleRepository.save(id, question.trim(), answer.trim()) }
    }

    fun delete(id: String) {
        viewModelScope.launch { ruleRepository.delete(id) }
    }
}
