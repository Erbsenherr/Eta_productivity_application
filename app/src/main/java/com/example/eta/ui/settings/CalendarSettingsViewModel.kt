package com.example.eta.ui.settings

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.CalendarRepository
import com.example.eta.data.repository.CalendarSyncService
import com.example.eta.data.repository.SyncOutcome
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.model.CalendarSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlinx.datetime.todayIn

/** How far ahead a manual refresh reads. Two weeks is what a planning phase sees. */
private const val REFRESH_DAYS = 14

data class CalendarSettingsUiState(
    val sources: List<CalendarSource> = emptyList(),
    val ignored: List<CalendarEvent> = emptyList(),
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
) {
    val connected: Boolean get() = sources.isNotEmpty()
}

/**
 * The Google-Kalender section of the settings tab.
 *
 * Its own view model rather than more fields on [SettingsViewModel]: everything
 * there is the standing questionnaire, saved through one path, and the calendar
 * shares none of that — it has a connection, a list of checkboxes and a refresh,
 * and mixing the two would give the save button two meanings.
 */
class CalendarSettingsViewModel(
    private val repository: CalendarRepository,
    private val syncService: CalendarSyncService,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val status = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)

    private val _consent = MutableStateFlow<PendingIntent?>(null)
    val consent: StateFlow<PendingIntent?> = _consent.asStateFlow()

    val uiState: StateFlow<CalendarSettingsUiState> = combine(
        repository.observeSources(),
        repository.observeIgnored(),
        busy,
        status,
        error,
    ) { sources, ignored, isBusy, message, failure ->
        CalendarSettingsUiState(
            sources = sources,
            ignored = ignored,
            busy = isBusy,
            status = message,
            error = failure,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarSettingsUiState(),
    )

    /** First contact: the account picker, then the list of calendars. */
    fun connect() {
        viewModelScope.launch { talkToGoogle { syncService.connect() } }
    }

    /** Reads the switched-on calendars again, a fortnight ahead. */
    fun refresh() {
        viewModelScope.launch {
            val from = clock.todayIn(timeZone)
            talkToGoogle { syncService.refresh(from, from.plus(DatePeriod(days = REFRESH_DAYS))) }
        }
    }

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

    fun setEnabled(source: CalendarSource, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(source, enabled) }
    }

    /** An ignored event becomes undecided, and the next planning step offers it again. */
    fun unignore(event: CalendarEvent) {
        viewModelScope.launch { repository.undecide(event) }
    }

    fun disconnect() {
        viewModelScope.launch {
            syncService.disconnect()
            status.value = "Verbindung getrennt. Bereits übernommene Termine bleiben bestehen."
            error.value = null
        }
    }

    fun dismissMessage() {
        status.value = null
        error.value = null
    }

    /**
     * The one shape every call to Google takes: busy, then a sentence.
     *
     * Named for what it does rather than `run`, which would sit one resolution
     * rule away from `kotlin.run` at every call site — and binding to that one
     * would silently skip the busy flag and the message, leaving a button that
     * looks inert while it works.
     */
    private suspend fun talkToGoogle(block: suspend () -> SyncOutcome) {
        busy.value = true
        error.value = null
        status.value = null
        when (val outcome = block()) {
            is SyncOutcome.Ok -> status.value = describe(outcome)
            is SyncOutcome.NeedsConsent -> _consent.value = outcome.pendingIntent
            is SyncOutcome.NotConnected -> error.value = "Kein Konto verbunden."
            is SyncOutcome.Failed -> error.value = outcome.reason
        }
        busy.value = false
    }

    private fun describe(outcome: SyncOutcome.Ok): String = buildString {
        append("${outcome.summary.calendars} Kalender gefunden")
        if (outcome.summary.events > 0) {
            append(", ${outcome.summary.events} Termine gelesen")
        }
        if (outcome.summary.pending > 0) {
            append(" — ${outcome.summary.pending} davon noch offen")
        }
        append(".")
    }
}
