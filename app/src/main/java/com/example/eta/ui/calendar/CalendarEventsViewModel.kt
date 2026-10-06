package com.example.eta.ui.calendar

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.AppointmentDetails
import com.example.eta.data.repository.CalendarImportService
import com.example.eta.data.repository.CalendarRepository
import com.example.eta.data.repository.CalendarSyncService
import com.example.eta.data.repository.ConflictingBlock
import com.example.eta.data.repository.ScheduleMaintenance
import com.example.eta.data.repository.SyncOutcome
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.ui.attributes.AppointmentAttributes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * The appointment that was just imported, and what it landed on.
 *
 * Held until every collision has an answer: the imported block keeps its slot
 * whatever happens — a calendar entry is a fact — but the recurring task now
 * underneath it is a decision, and the user makes it one at a time.
 */
data class ConflictPrompt(
    val appointment: String,
    val conflicts: List<ConflictingBlock>,
    /**
     * Why the last answer did not take, if it did not.
     *
     * Only "verschieben" can fail, and only for one reason: answering an earlier
     * conflict filled the stretch this one was going to move into. Saying so
     * beats moving the block somewhere nobody chose.
     */
    val refusal: String? = null,
)

data class CalendarStepUiState(
    val connected: Boolean = false,
    /** A sync is under way. The first one runs on the way into the screen. */
    val syncing: Boolean = true,
    val events: List<CalendarEvent> = emptyList(),
    /** Why the last sync did not work, if it did not. */
    val error: String? = null,
) {
    /** Nothing left to decide — everything the calendar offers is accounted for. */
    val settled: Boolean get() = !syncing && events.isEmpty()
}

/**
 * The calendar step, in both the places it appears.
 *
 * The weekly planning runs it over the week it is about to lay out; the evening
 * runs it over tomorrow, to catch what was put in the calendar during the day.
 * They differ only in [from] and [to] — the question, "what does the calendar
 * offer that this app has never been told about", is the same one, and the answer
 * is the same query.
 *
 * It runs **before** the day or the week is filled with ToDos, which is the whole
 * reason it exists as a step rather than as a background sync: an appointment
 * that arrives after the planning is a collision, and one that arrives before it
 * is simply part of the picture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarEventsViewModel(
    private val calendarRepository: CalendarRepository,
    private val syncService: CalendarSyncService,
    private val importService: CalendarImportService,
    private val scheduleMaintenance: ScheduleMaintenance,
    val from: LocalDate,
    val to: LocalDate,
) : ViewModel() {

    private val syncing = MutableStateFlow(true)
    private val connected = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val _consent = MutableStateFlow<PendingIntent?>(null)

    /** The screen Google wants shown. Only an Activity can launch it. */
    val consent: StateFlow<PendingIntent?> = _consent.asStateFlow()

    private val _conflicts = MutableStateFlow<ConflictPrompt?>(null)
    val conflicts: StateFlow<ConflictPrompt?> = _conflicts.asStateFlow()

    val uiState: StateFlow<CalendarStepUiState> = combine(
        calendarRepository.observePending(from, to),
        syncing,
        connected,
        error,
    ) { events, isSyncing, isConnected, failure ->
        CalendarStepUiState(
            connected = isConnected,
            syncing = isSyncing,
            events = events,
            error = failure,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarStepUiState(),
    )

    init {
        viewModelScope.launch {
            // The stretch has to exist before a collision with it can be seen: an
            // appointment cannot clash with a recurring task nobody has laid down
            // yet. Idempotent, so calling it here costs nothing.
            scheduleMaintenance.topUpUntil(to)
            refresh()
        }
    }

    /**
     * Reads the calendar again.
     *
     * Silent about not being connected: the evening walks through this step every
     * day, and someone who never linked an account must not meet an error for a
     * feature they are not using.
     */
    fun refresh() {
        viewModelScope.launch {
            syncing.value = true
            error.value = null
            when (val outcome = syncService.refresh(from, to)) {
                is SyncOutcome.Ok -> connected.value = true
                is SyncOutcome.NotConnected -> connected.value = false
                is SyncOutcome.NeedsConsent -> {
                    connected.value = true
                    _consent.value = outcome.pendingIntent
                }

                is SyncOutcome.Failed -> {
                    connected.value = calendarRepository.isConnected()
                    error.value = outcome.reason
                }
            }
            syncing.value = false
        }
    }

    /** The consent screen came back; carry on where the sync left off. */
    fun onConsent(data: Intent?) {
        _consent.value = null
        viewModelScope.launch {
            when (val outcome = syncService.completeConsent(data)) {
                is SyncOutcome.Failed -> error.value = outcome.reason
                else -> refresh()
            }
        }
    }

    fun dismissConsent() {
        _consent.value = null
    }

    /** Not the app's business. It leaves the list and never comes back as new. */
    fun ignore(event: CalendarEvent) {
        viewModelScope.launch { calendarRepository.ignore(event) }
    }

    /**
     * Turns the event into a card on its day — and, if that lands on something,
     * asks what should happen to what was there.
     */
    fun import(event: CalendarEvent, attributes: AppointmentAttributes) {
        viewModelScope.launch {
            val result = importService.import(
                event = event,
                details = AppointmentDetails(
                    category = attributes.category,
                    start = attributes.start,
                    duration = attributes.duration,
                    travelBefore = attributes.travelBefore,
                    returnAfter = attributes.returnAfter,
                    breakAfter = attributes.breakAfter,
                    endSound = attributes.endSound,
                    extras = attributes.extras,
                ),
            )
            if (result.conflicts.isNotEmpty()) {
                _conflicts.value = ConflictPrompt(event.title, result.conflicts)
            }
        }
    }

    /** The collision's first answer: the other thing is not happening. */
    fun cancelConflict(entry: BlockWithItem) {
        viewModelScope.launch {
            importService.cancelConflicting(entry)
            dropConflict(entry)
        }
    }

    /** The second: do it another day, out of the week list. */
    fun carryConflictIntoWeek(entry: BlockWithItem) {
        viewModelScope.launch {
            importService.carryConflictingIntoWeek(entry)
            dropConflict(entry)
        }
    }

    /**
     * The third: keep the activity, but end it where the appointment begins.
     *
     * The common case, and the reason the two whole-activity answers were not
     * enough — an appointment at noon does not usually cancel a morning.
     */
    fun shortenConflict(conflict: ConflictingBlock) {
        val shortened = conflict.shortened ?: return
        viewModelScope.launch {
            importService.shortenConflicting(conflict.entry, shortened.plannedDuration)
            dropConflict(conflict.entry)
        }
    }

    /** The fourth: keep it whole, and start it once the appointment is over. */
    fun pushConflict(conflict: ConflictingBlock) {
        val pushed = conflict.pushed ?: return
        viewModelScope.launch {
            if (importService.moveConflictingAfter(conflict.entry, pushed.start)) {
                dropConflict(conflict.entry)
            } else {
                // Re-checked at the moment it is applied, not at import: an
                // earlier answer in this same dialog can have taken the stretch.
                _conflicts.value = _conflicts.value?.copy(
                    refusal = "Dort ist inzwischen kein Platz mehr — " +
                        "der Zeitraum ist anderweitig belegt.",
                )
            }
        }
    }

    /** Leaving one alone is allowed; two things at once is sometimes simply true. */
    fun keepConflict(entry: BlockWithItem) = dropConflict(entry)

    private fun dropConflict(entry: BlockWithItem) {
        _conflicts.value = _conflicts.value?.let { prompt ->
            val left = prompt.conflicts.filterNot { it.entry.block.id == entry.block.id }
            if (left.isEmpty()) null else prompt.copy(conflicts = left, refusal = null)
        }
    }

    fun dismissConflicts() {
        _conflicts.value = null
    }
}
