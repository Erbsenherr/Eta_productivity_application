package com.example.eta.alarm

import com.example.eta.domain.sound.SoundEvent
import android.content.Context
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.SetupRepository
import com.example.eta.data.repository.SubtaskRepository
import com.example.eta.domain.planning.stepTimerEntries
import com.example.eta.domain.planning.PomodoroPhaseKind
import com.example.eta.domain.planning.TaskAnnouncement
import com.example.eta.domain.planning.TaskEventKind
import com.example.eta.domain.planning.eventsAt
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.nextTaskEvent
import com.example.eta.domain.planning.spokenAnnouncement
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * Announcing that something on the plan is starting — or that its time is up,
 * that a pomodoro phase turns over, or asking whether the user is still on it.
 *
 * One alarm at a time, always aimed at the next event still to come, and rebooked
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
 * Its **end** is announced only where `Item.endSound` asked for it. The pomodoro
 * boundaries come from the block's own rhythm, and the still-active question from
 * the setup's switch. All of them ride the one alarm: it points at the next
 * *event*, whichever kind that is, so a day of ten blocks is still one pending
 * intent.
 *
 * **One sound per kind per ring.** Two tasks starting on the same minute is
 * ordinary, and two copies of the same sound on top of each other is only louder.
 * Read out instead — `UserSetup.taskAnnouncement` — every task is named, since
 * the name is the point; see [EtaSpeech] and `spokenAnnouncement`.
 */
class TaskStartCoordinator(
    private val context: Context,
    private val planRepository: PlanRepository,
    private val setupRepository: SetupRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    /**
     * False in the tutorial's sandbox: its tasks are examples, and an alarm for
     * one would ring on the user's real phone — in place of their own.
     */
    private val enabled: Boolean = true,
    /** Step 49: the timers of routine steps ring as well; null leaves them silent. */
    private val subtaskRepository: SubtaskRepository? = null,
) {

    private val scheduler = TaskStartAlarmScheduler(context)

    /**
     * Points the alarm at the next event still to come.
     *
     * Called wherever the plan can have changed: app start, leaving a flow, a
     * boot, an edit on the dashboard, a saved setting, and after every ring.
     * Cheap enough to call often — two day queries and one `AlarmManager` call.
     */
    suspend fun reschedule() {
        if (!enabled) return
        val blocks = blocksOfInterest()
        val next = nextTaskEvent(blocks, clock.now(), timeZone, stillActivePerDay(), stepTimers(blocks))
        if (next == null) scheduler.cancel() else scheduler.schedule(next)
    }

    /**
     * The alarm arrived for [at]: say what happens then, and book the next one.
     *
     * Matched against the minute the alarm was *laid down for* rather than
     * against the clock, so an alarm delivered late still announces the right
     * thing instead of the nearest thing.
     */
    suspend fun onRing(at: Instant) {
        val blocks = blocksOfInterest()
        val events = eventsAt(blocks, at, timeZone, stillActivePerDay(), stepTimers(blocks))
        val minute = at.toLocalDateTime(timeZone).time.minuteOfDay()

        events.forEach { event ->
            when (event.kind) {
                TaskEventKind.START -> TaskStartNotifications.show(context, event.entry)
                TaskEventKind.END -> TaskEndNotifications.show(context, event.entry)
                TaskEventKind.STILL_ACTIVE -> TaskNudgeNotifications.showStillActive(context, event.entry)
                TaskEventKind.POMODORO_PAUSE ->
                    TaskNudgeNotifications.showPomodoro(context, event.entry, PomodoroPhaseKind.PAUSE, minute, at)

                TaskEventKind.POMODORO_WORK ->
                    TaskNudgeNotifications.showPomodoro(context, event.entry, PomodoroPhaseKind.WORK, minute, at)

                TaskEventKind.TIMER_DONE -> TaskNudgeNotifications.showTimerDone(context, event.entry)
            }
        }
        // Booked before anything is said: reading a name out takes seconds, and
        // the next alarm is the half that must not be lost.
        reschedule()

        val setup = setupRepository.find()
        val spoken = setup?.taskAnnouncement == TaskAnnouncement.SPEECH &&
            EtaSpeech.speak(context, spokenAnnouncement(events, setup.speakNotes))
        // Also the way out when no speech engine answers: the sound it replaced.
        if (!spoken) {
            events.map { it.kind }.distinct().forEach { kind -> EtaSound.play(context, soundOf(kind)) }
        }
    }

    private fun soundOf(kind: TaskEventKind): SoundEvent = when (kind) {
        TaskEventKind.START -> SoundEvent.TASK_START
        TaskEventKind.END -> SoundEvent.TASK_END
        TaskEventKind.STILL_ACTIVE -> SoundEvent.STILL_ACTIVE
        TaskEventKind.POMODORO_PAUSE -> SoundEvent.POMODORO_PAUSE
        TaskEventKind.POMODORO_WORK -> SoundEvent.POMODORO_WORK
        TaskEventKind.TIMER_DONE -> SoundEvent.TIMER_DONE
    }

    /** Zero while the question is switched off, which asks nothing. */
    private suspend fun stillActivePerDay(): Int =
        setupRepository.find()
            ?.takeIf { it.stillActiveReminder }
            ?.stillActivePerDay
            ?: 0

    /**
     * Today and tomorrow.
     *
     * Tomorrow is needed because the next start after a late evening is a
     * morning: looking only at today would leave the alarm cancelled overnight
     * and the first block of the next day silent.
     */
    /** The steps' timers on the days of [blocks] — see `stepTimerEntries`. */
    private suspend fun stepTimers(blocks: List<BlockWithItem>): List<BlockWithItem> {
        val repository = subtaskRepository ?: return emptyList()
        val today: LocalDate = clock.todayIn(timeZone)
        val tomorrow = today.plus(DatePeriod(days = 1))
        val timers = repository.timersBetween(today, tomorrow)
        if (timers.isEmpty()) return emptyList()
        val checked = (repository.checkedByBlock(today) + repository.checkedByBlock(tomorrow))
            .flatMap { (block, ids) -> ids.map { block to it } }
            .toSet()
        return stepTimerEntries(blocks, timers, repository.stepsByItem(), checked)
    }

    private suspend fun blocksOfInterest(): List<BlockWithItem> {
        val today: LocalDate = clock.todayIn(timeZone)
        return planRepository.findDay(today) +
            planRepository.findDay(today.plus(DatePeriod(days = 1)))
    }
}
