package com.example.erik_iteration_2.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.example.erik_iteration_2.domain.planning.PlanningPhase
import kotlin.time.Instant

/** Intent keys and actions the alarm and the notification pass around. */
object PlanningAlarmContract {
    const val ACTION_RING = "com.example.erik_iteration_2.PLANNING_RING"
    const val ACTION_SNOOZE = "com.example.erik_iteration_2.PLANNING_SNOOZE"
    const val EXTRA_PHASE = "phase"

    /** Set on the launch intent when the user tapped "NOTFALL". */
    const val EXTRA_DEFER_PHASE = "defer_phase"

    /** Set when the user answered the alarm and wants to get on with the phase. */
    const val EXTRA_OPEN_PHASE = "open_phase"

    fun requestCode(phase: PlanningPhase): Int = 1000 + phase.ordinal
}

/**
 * Puts the next planning phase on the system alarm clock.
 *
 * `setAlarmClock` on purpose: this *is* an alarm the user set, it survives doze,
 * and it shows in the status bar so an alarm that will interrupt the evening is
 * never a surprise. Android 12 gates exact alarms behind a permission the user can
 * refuse, so an inexact window is the fallback — late is far better than silent.
 */
class PlanningAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    fun schedule(phase: PlanningPhase, at: Instant) {
        val manager = alarmManager ?: return
        val fireAt = at.toEpochMilliseconds()
        val operation = ringingIntent(phase)

        if (canScheduleExact(manager)) {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, showIntent()), operation)
        } else {
            // A ten-minute window: still recognisably "the planning phase", and it
            // does not need a permission the user has already declined.
            manager.setWindow(
                AlarmManager.RTC_WAKEUP,
                fireAt,
                10 * 60 * 1000L,
                operation,
            )
        }
    }

    fun cancel(phase: PlanningPhase) {
        alarmManager?.cancel(ringingIntent(phase))
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun ringingIntent(phase: PlanningPhase): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            PlanningAlarmContract.requestCode(phase),
            Intent(context, PlanningAlarmReceiver::class.java).apply {
                action = PlanningAlarmContract.ACTION_RING
                putExtra(PlanningAlarmContract.EXTRA_PHASE, phase.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** What the status bar's alarm icon opens. */
    private fun showIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

internal fun launchIntent(context: Context): Intent =
    Intent().setClassName(context, "com.example.erik_iteration_2.MainActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
