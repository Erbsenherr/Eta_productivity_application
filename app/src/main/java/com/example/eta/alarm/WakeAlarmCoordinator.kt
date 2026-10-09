package com.example.eta.alarm

import android.content.Context
import com.example.eta.data.repository.SetupRepository
import com.example.eta.domain.planning.WAKE_SNOOZE
import com.example.eta.domain.planning.nextWake
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The wake alarm, from the switch in the settings to the ringing and back.
 *
 * Off unless the user asked for it: `UserSetup.wakeAlarm` is the switch, and
 * `wakeTime` is the hour, so the alarm cannot drift away from the hour the
 * planner shades as the end of the night.
 *
 * Rescheduling follows the same rule as everything else in `alarm/` — it happens
 * on every path, ringing included. An alarm clock that forgets to arm tomorrow
 * has failed silently, which is the worst way for an alarm clock to fail.
 */
class WakeAlarmCoordinator(
    private val context: Context,
    private val setupRepository: SetupRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    /**
     * False in the tutorial's sandbox: its tasks are examples, and an alarm for
     * one would ring on the user's real phone — in place of their own.
     */
    private val enabled: Boolean = true,
) {

    private val scheduler = WakeAlarmScheduler(context)

    /** After the setup, after a boot, and after the switch is flipped. */
    suspend fun reschedule() {
        if (!enabled) return
        val setup = setupRepository.find()
        val due = setup?.let { nextWake(it, clock.now().toLocalDateTime(timeZone)) }
        if (due == null) {
            scheduler.cancel()
            // Turning the alarm off while it is ringing has to silence it too,
            // or the switch would look broken at the only moment it matters.
            if (!WakeAlarmRinger.isRinging) WakeAlarmNotifications.dismiss(context)
        } else {
            scheduler.schedule(due.toInstant(timeZone))
        }
    }

    /** The alarm went off: ring, and leave tomorrow's armed behind it. */
    suspend fun onRing() {
        WakeAlarmNotifications.show(context)
        WakeAlarmRinger.start(context)
        reschedule()
    }

    /**
     * Nine more minutes.
     *
     * The ringing stops but the notification does not come back until it rings
     * again, which is the difference between snoozing and dismissing: nothing on
     * screen says an alarm is owed, only the alarm itself remembers.
     */
    fun snooze() {
        WakeAlarmRinger.stop(context)
        WakeAlarmNotifications.dismiss(context)
        scheduler.schedule(clock.now() + WAKE_SNOOZE)
    }

    /** "Aus": silence it and put tomorrow's back in place. */
    suspend fun stop() {
        WakeAlarmRinger.stop(context)
        WakeAlarmNotifications.dismiss(context)
        reschedule()
    }

    /**
     * The same from a screen, which cannot wait for a coroutine to finish before
     * it closes. Fire and forget on an application-lifetime scope: the alarm is
     * already silenced by the time this returns, and only the rearming is async.
     */
    fun stopAndRearm() {
        WakeAlarmRinger.stop(context)
        WakeAlarmNotifications.dismiss(context)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { reschedule() }
    }
}
