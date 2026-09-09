package com.example.erik_iteration_2.alarm

import android.content.Context
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.data.repository.PlanRepository
import com.example.erik_iteration_2.domain.planning.TaskEventKind
import com.example.erik_iteration_2.domain.planning.eventsAt
import com.example.erik_iteration_2.domain.planning.nextTaskEvent
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Announcing that something on the plan is starting — or that its time is up.
 *
 * One alarm at a time, always aimed at the next block still to come, and rebooked
 * on every path — the same rule the planning alarm follows, for the same reason:
 * an alarm that fires and forgets to set the next one has switched the rest of
 * the day off, and nothing would say so.
 *
 * Unlike the planning alarm this does **not** stay quiet while the app is open.
 * That exemption exists because a planning phase is something you do *in* the
 * app, so having it open is evidence you are already doing it. A task beginning
 * is about the world, and looking at the screen is no evidence at all.
 *
 * Every block announces its **start**, including the frame of the day the
 * questionnaire lays down — Morgenzeit, Pause, Freizeit. That is what "a new
 * activity begins according to the plan" says, and `ItemRole` is the handle to
 * narrow it with if it turns out to be too much.
 *
 * Its **end** is announced only where `Item.endSound` asked for it, which the
 * concretizing step ticks for new cards and the migration leaves off for
 * everything that predates the switch — the frame of the day would otherwise
 * start chiming all day for a user who only upgraded. Both kinds ride the one
 * alarm: it points at the next *event*, whichever kind that is, so a day of ten
 * blocks is still one pending intent.
 */
class TaskStartCoordinator(
    private val context: Context,
    private val planRepository: PlanRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    private val scheduler = TaskStartAlarmScheduler(context)

    /**
     * Points the alarm at the next block still to come.
     *
     * Called wherever the plan can have changed: app start, leaving a flow, a
     * boot, an edit on the dashboard, and after every ring. Cheap enough to call
     * often — two day queries and one `AlarmManager` call.
     */
    suspend fun reschedule() {
        val next = nextTaskEvent(blocksOfInterest(), clock.now(), timeZone)
        if (next == null) scheduler.cancel() else scheduler.schedule(next)
    }

    /**
     * The alarm arrived for [at]: say what begins then, and book the next one.
     *
     * Matched against the minute the alarm was *laid down for* rather than
     * against the clock, so an alarm delivered late still announces the right
     * thing instead of the nearest thing.
     */
    suspend fun onRing(at: Instant) {
        eventsAt(blocksOfInterest(), at, timeZone).forEach { event ->
            when (event.kind) {
                TaskEventKind.START -> TaskStartNotifications.show(context, event.entry)
                TaskEventKind.END -> TaskEndNotifications.show(context, event.entry)
            }
        }

        reschedule()
    }

    /**
     * Today and tomorrow.
     *
     * Tomorrow is needed because the next start after a late evening is a
     * morning: looking only at today would leave the alarm cancelled overnight
     * and the first block of the next day silent.
     */
    private suspend fun blocksOfInterest(): List<BlockWithItem> {
        val today: LocalDate = clock.now().toLocalDateTime(timeZone).date
        return planRepository.findDay(today) +
            planRepository.findDay(today.plus(DatePeriod(days = 1)))
    }
}
