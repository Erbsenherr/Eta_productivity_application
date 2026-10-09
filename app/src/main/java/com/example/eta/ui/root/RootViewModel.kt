package com.example.eta.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.alarm.PlanningAlarmCoordinator
import com.example.eta.alarm.ReminderCoordinator
import com.example.eta.alarm.TaskStartCoordinator
import com.example.eta.data.repository.ScheduleMaintenance
import com.example.eta.data.repository.SetupRepository
import com.example.eta.data.repository.WeekPlanningService
import com.example.eta.ui.tutorial.TutorialStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Which screen the app opens on.
 *
 * [Loading] is a state of its own rather than "no setup yet": without it the
 * questionnaire would flash up for a moment on every cold start before the
 * stored answers arrive from the database.
 */
enum class RootDestination { Loading, Setup, Dashboard }

class RootViewModel(
    private val setupRepository: SetupRepository,
    private val planningAlarmCoordinator: PlanningAlarmCoordinator,
    private val taskStartCoordinator: TaskStartCoordinator,
    private val reminderCoordinator: ReminderCoordinator,
    private val scheduleMaintenance: ScheduleMaintenance,
    private val weekPlanningService: WeekPlanningService,
    private val tutorialStore: TutorialStore,
) : ViewModel() {

    val destination: StateFlow<RootDestination> = setupRepository.observe()
        .map { if (it == null) RootDestination.Setup else RootDestination.Dashboard }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RootDestination.Loading,
        )

    /**
     * Lays down the planning alarms, and tops the standing schedule up while it is
     * at it — the recurring tasks would otherwise run out at the setup's horizon.
     */
    fun rescheduleAlarms() {
        viewModelScope.launch {
            scheduleMaintenance.topUp()
            // The devaluation is a fact about the calendar, so it lands on the
            // first launch after its day rather than waiting for a screen visit.
            weekPlanningService.applyInflation()
            planningAlarmCoordinator.rescheduleAll()
            // Runs on leaving every flow, which is where the day's blocks get
            // moved, added and taken off — so this is where the announcement
            // of the next one has to be re-aimed.
            taskStartCoordinator.reschedule()
            reminderCoordinator.reschedule()
        }
    }

    /**
     * Debug only: wipes every table. No navigation call is needed afterwards —
     * clearing the setup row makes [destination] emit `Setup` by itself.
     */
    fun resetEverything() {
        viewModelScope.launch {
            // "Before the first start" includes the welcome page and the
            // tutorial that follows the questionnaire. First, so the page is
            // already owed when the missing setup row swaps the screen.
            tutorialStore.reset()
            setupRepository.resetEverything()
            // Without a setup there is nothing to ring for; this clears the alarms.
            planningAlarmCoordinator.rescheduleAll()
            taskStartCoordinator.reschedule()
            reminderCoordinator.reschedule()
        }
    }
}
