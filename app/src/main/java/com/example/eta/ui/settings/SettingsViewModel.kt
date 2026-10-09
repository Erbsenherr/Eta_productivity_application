package com.example.eta.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.alarm.TaskStartCoordinator
import com.example.eta.alarm.WakeAlarmCoordinator
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.ReminderRepository
import com.example.eta.data.backup.BackupResult
import com.example.eta.data.backup.BackupService
import com.example.eta.data.repository.CatchUpResult
import com.example.eta.data.repository.CatchUpService
import com.example.eta.data.repository.PlanningPhaseService
import com.example.eta.data.repository.SetupRepository
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.planning.nextTaskEvent
import com.example.eta.domain.reminder.nextReminderAlarm
import com.example.eta.domain.planning.PlanningPhase
import com.example.eta.domain.planning.nextWake
import com.example.eta.domain.streak.CATCH_UP_PHRASE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.domain.subtask.drafts
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the screen has to say after a backup, a restore or a save. */
sealed interface SettingsMessage {
    data class CaughtUp(val date: LocalDate) : SettingsMessage
    data object Saved : SettingsMessage
    data class Exported(val bytes: Long) : SettingsMessage
    data class Imported(val bytes: Long) : SettingsMessage
    data class Failed(val reason: String) : SettingsMessage
}

/** When each of the three alarms is next due. Null means it is switched off. */
data class AlarmSchedule(
    val daily: Instant? = null,
    val weekly: Instant? = null,
    val wake: Instant? = null,
    /** The next start, end, pomodoro turn or question on today's and tomorrow's plan. */
    val task: Instant? = null,
    val reminder: Instant? = null,
)

class SettingsViewModel(
    private val setupRepository: SetupRepository,
    private val backupService: BackupService,
    private val catchUpService: CatchUpService,
    private val phaseService: PlanningPhaseService,
    private val wakeAlarmCoordinator: WakeAlarmCoordinator,
    private val taskStartCoordinator: TaskStartCoordinator,
    private val planRepository: PlanRepository,
    private val reminderRepository: ReminderRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /**
     * The stored answers, loaded once into an editable draft.
     *
     * Null until they arrive — the screen shows nothing rather than a set of
     * defaults the user never chose and might save by accident.
     */
    private val _draft = MutableStateFlow<UserSetup?>(null)
    val draft: StateFlow<UserSetup?> = _draft.asStateFlow()

    /**
     * The morning routine's steps. Not part of the draft and not waiting for
     * "Einrichtung sichern": they are rows of their own, and the builder's
     * Sichern is their commit point, as it is on the Listen tab.
     */
    val morningSteps: StateFlow<List<SubtaskDraft>> = setupRepository.observeMorningSteps()
        .map { it.drafts() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveMorningSteps(steps: List<SubtaskDraft>) {
        viewModelScope.launch { setupRepository.saveMorningSteps(steps) }
    }

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message.asStateFlow()

    private val _alarms = MutableStateFlow(AlarmSchedule())

    /**
     * What is actually armed, read from the same source the schedulers use.
     *
     * Shown because "there was no alarm" is otherwise unanswerable: the user
     * cannot tell an alarm that is due tomorrow from one that was never set.
     */
    val alarms: StateFlow<AlarmSchedule> = _alarms.asStateFlow()

    init {
        viewModelScope.launch {
            _draft.value = setupRepository.find()
            _openDays.value = catchUpService.openDays()
            refreshAlarms()
        }
    }

    private suspend fun refreshAlarms() {
        val setup = setupRepository.find()
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        _alarms.value = AlarmSchedule(
            task = nextTaskEvent(
                blocks = planRepository.findDay(today) +
                    planRepository.findDay(today.plus(DatePeriod(days = 1))),
                now = now,
                timeZone = timeZone,
                stillActivePerDay = setup?.takeIf { it.stillActiveReminder }?.stillActivePerDay ?: 0,
            ),
            reminder = nextReminderAlarm(reminderRepository.findPending(), now),
            daily = phaseService.nextDue(PlanningPhase.DAILY),
            weekly = phaseService.nextDue(PlanningPhase.WEEKLY),
            wake = setup
                ?.let { nextWake(it, clock.now().toLocalDateTime(timeZone)) }
                ?.toInstant(timeZone),
        )
    }

    fun update(transform: (UserSetup) -> UserSetup) {
        _draft.update { it?.let(transform) }
    }

    /**
     * Flips an "Advanced Features" switch, at once.
     *
     * Written straight to the stored setup rather than waiting for "Einrichtung
     * sichern": the switch that hid the account used to do nothing until that
     * button was found and pressed, which read as a switch that did not work.
     * The draft is changed too, or the next save would write the old answer back.
     */
    fun setFeature(transform: (UserSetup) -> UserSetup) {
        _draft.update { it?.let(transform) }
        viewModelScope.launch { setupRepository.update(transform) }
    }

    /**
     * Saves the standing configuration.
     *
     * Not through `complete`, the questionnaire's path: that regenerates every
     * task the questionnaire ever laid down, and those are edited on the Listen
     * tab now. `saveSettings` regenerates only the two that hang off the night.
     */
    fun save() {
        val setup = _draft.value ?: return
        viewModelScope.launch {
            setupRepository.saveSettings(setup)
            // The wake alarm hangs off two of these answers, so saving them is
            // the moment it has to be re-armed or taken down.
            wakeAlarmCoordinator.reschedule()
            // So does the task alarm: the still-active switch, and the morning
            // blocks that were just laid down again.
            taskStartCoordinator.reschedule()
            refreshAlarms()
            _message.value = SettingsMessage.Saved
        }
    }

    fun export(target: Uri) {
        viewModelScope.launch {
            _message.value = when (val result = withContext(Dispatchers.IO) { backupService.export(target) }) {
                is BackupResult.Ok -> SettingsMessage.Exported(result.bytes)
                is BackupResult.Failed -> SettingsMessage.Failed(result.reason)
            }
        }
    }

    fun import(source: Uri) {
        viewModelScope.launch {
            _message.value = when (val result = withContext(Dispatchers.IO) { backupService.import(source) }) {
                is BackupResult.Ok -> SettingsMessage.Imported(result.bytes)
                is BackupResult.Failed -> SettingsMessage.Failed(result.reason)
            }
        }
    }

    /** Past days the app planned but never settled — bounded by the catch-up window. */
    private val _openDays = MutableStateFlow<List<LocalDate>>(emptyList())
    val openDays: StateFlow<List<LocalDate>> = _openDays.asStateFlow()

    private val _phrase = MutableStateFlow("")
    val phrase: StateFlow<String> = _phrase.asStateFlow()

    /** Only the exact words unlock it; anything else leaves the days untouchable. */
    val phraseAccepted: StateFlow<Boolean> = _phrase
        .map { it.trim() == CATCH_UP_PHRASE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setPhrase(value: String) {
        _phrase.value = value
    }

    fun refreshOpenDays() {
        viewModelScope.launch { _openDays.value = catchUpService.openDays() }
    }

    fun catchUp(date: LocalDate) {
        viewModelScope.launch {
            _message.value = when (val result = catchUpService.catchUp(date, _phrase.value)) {
                is CatchUpResult.Resolved -> SettingsMessage.CaughtUp(result.date)
                is CatchUpResult.Refused -> SettingsMessage.Failed(result.reason)
            }
            _openDays.value = catchUpService.openDays()
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
