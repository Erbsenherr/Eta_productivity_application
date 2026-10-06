package com.example.eta.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.SetupRepository
import com.example.eta.domain.setup.SetupConflict
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.conflicts
import com.example.eta.domain.setup.freeMinutesPerWeek
import com.example.eta.domain.setup.recurringItems
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the summary step reports back about the answers so far. */
data class SetupOutlook(
    val conflicts: List<SetupConflict>,
    val freeMinutesPerWeek: Int,
    val generatedTaskCount: Int,
)

class SetupViewModel(
    private val setupRepository: SetupRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _draft = MutableStateFlow(UserSetup.draft(clock.now()))
    val draft: StateFlow<UserSetup> = _draft.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /**
     * Recomputed on every answer, because the concept asks for double bookings to
     * be pointed out *while* the questionnaire is being filled in, not at the end.
     */
    val outlook: StateFlow<SetupOutlook> = _draft
        .map { setup ->
            SetupOutlook(
                conflicts = setup.conflicts(),
                freeMinutesPerWeek = setup.freeMinutesPerWeek(),
                generatedTaskCount = setup.recurringItems(clock.now()).size,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SetupOutlook(emptyList(), 0, 0),
        )

    fun update(transform: (UserSetup) -> UserSetup) {
        _draft.update(transform)
    }

    /**
     * Writes the answers and lays down the schedule. No callback is needed: the
     * root screen watches the stored setup and swaps itself for the dashboard.
     *
     * The flag is cleared again afterwards because this view model outlives the
     * screen — it lives in the activity's store, so the debug reset comes back to
     * this same instance and would otherwise find its "Fertig" button stuck.
     * The draft is deliberately *not* re-seeded: coming back to the questionnaire
     * with the previous answers still filled in is the friendlier debug loop, and
     * nothing of it is persisted.
     */
    fun finish() {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            setupRepository.complete(_draft.value)
            _saving.value = false
        }
    }
}
