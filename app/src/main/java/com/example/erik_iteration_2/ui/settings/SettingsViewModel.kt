package com.example.erik_iteration_2.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erik_iteration_2.alarm.WakeAlarmCoordinator
import com.example.erik_iteration_2.data.backup.BackupResult
import com.example.erik_iteration_2.data.backup.BackupService
import com.example.erik_iteration_2.data.repository.CatchUpResult
import com.example.erik_iteration_2.data.repository.CatchUpService
import com.example.erik_iteration_2.data.repository.PlanningPhaseService
import com.example.erik_iteration_2.data.repository.SetupRepository
import com.example.erik_iteration_2.domain.setup.UserSetup
import com.example.erik_iteration_2.domain.setup.conflicts
import com.example.erik_iteration_2.domain.planning.PlanningPhase
import com.example.erik_iteration_2.domain.planning.nextWake
import com.example.erik_iteration_2.domain.streak.CATCH_UP_PHRASE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the screen has to say after a backup, a restore or a save. */
sealed interface SettingsMessage {
    data class CaughtUp(val date: LocalDate) : SettingsMessage
    data class Saved(val conflicts: Int) : SettingsMessage
    data class Exported(val bytes: Long) : SettingsMessage
    data class Imported(val bytes: Long) : SettingsMessage
    data class Failed(val reason: String) : SettingsMessage
}

/** When each of the three alarms is next due. Null means it is switched off. */
data class AlarmSchedule(
    val daily: Instant? = null,
    val weekly: Instant? = null,
    val wake: Instant? = null,
)

class SettingsViewModel(
    private val setupRepository: SetupRepository,
    private val backupService: BackupService,
    private val catchUpService: CatchUpService,
    private val phaseService: PlanningPhaseService,
    private val wakeAlarmCoordinator: WakeAlarmCoordinator,
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
        _alarms.value = AlarmSchedule(
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
     * Saves the answers through the same path the questionnaire uses.
     *
     * `complete` regenerates the standing schedule from them and retires what no
     * longer follows — which is exactly what changing an answer should do, and the
     * reason editing here does not need its own machinery.
     */
    fun save() {
        val setup = _draft.value ?: return
        viewModelScope.launch {
            setupRepository.complete(setup)
            // The wake alarm hangs off two of these answers, so saving them is
            // the moment it has to be re-armed or taken down.
            wakeAlarmCoordinator.reschedule()
            refreshAlarms()
            _message.value = SettingsMessage.Saved(setup.conflicts().size)
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
