package com.example.erik_iteration_2.alarm

import android.content.Context
import com.example.erik_iteration_2.data.repository.PlanningPhaseService
import com.example.erik_iteration_2.domain.planning.PlanningPhase
import com.example.erik_iteration_2.domain.planning.deferredFrom
import com.example.erik_iteration_2.domain.planning.shouldRing
import com.example.erik_iteration_2.domain.planning.snoozedFrom
import kotlin.time.Clock

/**
 * Everything the alarm does once it goes off.
 *
 * Rescheduling happens on *every* path, including the ones where nothing rings.
 * An alarm that decides not to sound and then forgets to set the next one has
 * quietly switched the whole mechanism off — the failure would be invisible until
 * someone noticed the app had stopped asking.
 */
class PlanningAlarmCoordinator(
    private val context: Context,
    private val phaseService: PlanningPhaseService,
    private val isAppInForeground: () -> Boolean,
    private val clock: Clock = Clock.System,
) {

    private val scheduler = PlanningAlarmScheduler(context)

    /** After the setup, after a boot, and after a phase is finished. */
    suspend fun rescheduleAll() {
        PlanningPhase.entries.forEach { phase ->
            PlanningNotifications.dismiss(context, phase)
            val due = phaseService.nextDue(phase)
            if (due == null) scheduler.cancel(phase) else scheduler.schedule(phase, due)
        }
    }

    /**
     * The alarm went off. Rings unless the user is already in the app or the phase
     * is done, and books the next attempt either way.
     */
    suspend fun onRing(phase: PlanningPhase) {
        val completed = phaseService.isCompleted(phase)

        if (shouldRing(isAppInForeground(), completed)) {
            PlanningNotifications.show(context, phase)
            // Keep asking every five minutes, as the concept insists.
            scheduler.schedule(phase, phaseService.retryAt())
            return
        }

        PlanningNotifications.dismiss(context, phase)
        val due = phaseService.nextDue(phase)
        // Done for today: forward to the phase's next regular hour. Merely in the
        // app: ask again shortly, since being in the app is not doing the phase.
        val next = if (completed) due else phaseService.retryAt()
        if (next == null) scheduler.cancel(phase) else scheduler.schedule(phase, next)
    }

    fun snooze(phase: PlanningPhase) {
        PlanningNotifications.dismiss(context, phase)
        scheduler.schedule(phase, snoozedFrom(clock.now()))
    }

    /** The "NOTFALL": pushed back by a number of hours the user names. */
    fun defer(phase: PlanningPhase, hours: Int) {
        PlanningNotifications.dismiss(context, phase)
        scheduler.schedule(phase, deferredFrom(clock.now(), hours))
    }
}
