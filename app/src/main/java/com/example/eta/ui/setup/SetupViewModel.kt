package com.example.eta.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.RecurringTaskService
import com.example.eta.data.repository.SetupRepository
import com.example.eta.domain.model.Item
import com.example.eta.domain.setup.SetupConflict
import com.example.eta.domain.setup.SetupPart
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.conflicts
import com.example.eta.domain.setup.freeMinutesPerWeek
import com.example.eta.domain.setup.recurringItems
import com.example.eta.domain.setup.skipping
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.ui.attributes.RecurringAttributes
import com.example.eta.ui.attributes.toEdit
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.eta.domain.setup.SetupRoutine

/** What the summary step reports back about the answers so far. */
data class SetupOutlook(
    val conflicts: List<SetupConflict>,
    val freeMinutesPerWeek: Int,
    val generatedTaskCount: Int,
    /**
     * The standing tasks the answers so far would lay down — what a task of the
     * user's own is compared against for its overlap warning.
     */
    val generated: List<Item> = emptyList(),
)

/**
 * A standing task the user added to the checklist: not one of the five the
 * questionnaire knows, so it has no page of its own and is asked with the
 * ordinary form. Lives here until "Fertig" — then it is a standing task like
 * any other, made by the call the Listen tab makes.
 */
data class CustomTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val attributes: RecurringAttributes = RecurringAttributes(),
)

class SetupViewModel(
    private val setupRepository: SetupRepository,
    private val recurringTaskService: RecurringTaskService,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _draft = MutableStateFlow(UserSetup.draft(clock.now()))
    val draft: StateFlow<UserSetup> = _draft.asStateFlow()

    /**
     * The pages the user chose to leave out. Kept beside the draft rather than
     * written into it, so the answers on a skipped page are still there when it
     * is taken back; [effective] is the draft with them removed.
     *
     * Since step 46 this is what the checklist of standing tasks writes: a box
     * unticked there is a part skipped here. Free time still has its own
     * "Überspringen".
     */
    private val _skipped = MutableStateFlow(emptySet<SetupPart>())
    val skipped: StateFlow<Set<SetupPart>> = _skipped.asStateFlow()

    private val effective = combine(_draft, _skipped) { draft, skipped -> draft.skipping(skipped) }

    private val _custom = MutableStateFlow(emptyList<CustomTask>())
    val custom: StateFlow<List<CustomTask>> = _custom.asStateFlow()

    /**
     * The routines' steps, held until there is a task to put them on. The
     * routines are laid down by `complete`; [finish] writes these onto them.
     */
    private val _routineSteps = MutableStateFlow(emptyMap<SetupRoutine, List<SubtaskDraft>>())
    val routineSteps: StateFlow<Map<SetupRoutine, List<SubtaskDraft>>> = _routineSteps.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /**
     * Whether the questionnaire's last screen is owed: the standing week as a
     * timetable, to be looked over and pulled straight before the app begins.
     *
     * Set **before** the answers are stored — storing them is what swaps the
     * root screen, and by then this has to say where to. Not persisted: a
     * process that dies on that screen comes back to the dashboard, with the
     * same timetable one tab away.
     */
    private val _weekReview = MutableStateFlow(false)
    val weekReview: StateFlow<Boolean> = _weekReview.asStateFlow()

    fun endWeekReview() {
        _weekReview.value = false
    }

    /**
     * Recomputed on every answer, because the concept asks for double bookings to
     * be pointed out *while* the questionnaire is being filled in, not at the end.
     */
    val outlook: StateFlow<SetupOutlook> = effective
        .map { setup ->
            val generated = setup.recurringItems(clock.now())
            SetupOutlook(
                conflicts = setup.conflicts(),
                freeMinutesPerWeek = setup.freeMinutesPerWeek(),
                generatedTaskCount = generated.size,
                generated = generated,
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

    fun skip(part: SetupPart) = _skipped.update { it + part }

    fun unskip(part: SetupPart) = _skipped.update { it - part }

    fun setRoutineSteps(routine: SetupRoutine, steps: List<SubtaskDraft>) {
        _routineSteps.update { it + (routine to steps) }
    }

    /** A task of the user's own, by name; the rest is asked on its page. */
    fun addCustom(name: String) {
        if (name.isBlank()) return
        _custom.update { it + CustomTask(name = name.trim()) }
    }

    fun removeCustom(id: String) = _custom.update { tasks -> tasks.filterNot { it.id == id } }

    fun updateCustom(id: String, transform: (CustomTask) -> CustomTask) =
        _custom.update { tasks -> tasks.map { if (it.id == id) transform(it) else it } }

    /**
     * Writes the answers and lays down the schedule. No callback is needed: the
     * root screen watches the stored setup and swaps itself — for the week
     * review first, see [weekReview].
     *
     * In three steps, in this order: the answers and the tasks that follow from
     * them; the morning routine's steps, which need its task to exist; and the
     * user's own tasks, through the Listen tab's own call, so they are standing
     * tasks like any made there.
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
        _weekReview.value = true
        viewModelScope.launch {
            setupRepository.complete(_draft.value.skipping(_skipped.value))
            _routineSteps.value.forEach { (routine, steps) ->
                if (steps.isNotEmpty()) setupRepository.saveRoutineSteps(routine, steps)
            }
            _custom.value
                .filter { it.name.isNotBlank() && it.attributes.weekdays.isNotEmpty() }
                .forEach { task ->
                    recurringTaskService.createGroup(task.attributes.toEdit(task.name.trim(), note = null))
                }
            _saving.value = false
        }
    }
}
