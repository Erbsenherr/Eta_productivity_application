package com.example.erik_iteration_2.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.example.erik_iteration_2.ErikApplication
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WakeAlarmContract {
    const val ACTION_RING = "com.example.erik_iteration_2.WAKE_RING"
    const val ACTION_SNOOZE = "com.example.erik_iteration_2.WAKE_SNOOZE"
    const val ACTION_STOP = "com.example.erik_iteration_2.WAKE_STOP"

    const val REQUEST_CODE = 1200
}

/**
 * The wake alarm.
 *
 * This is the one place in the app where `setAlarmClock` is unambiguously right:
 * it *is* an alarm clock. It is exempt from doze, it puts the next-alarm entry in
 * the status bar — which for a wake alarm is a feature, not noise, since being
 * able to see that it is armed is half the reassurance — and it survives the
 * battery restrictions an inexact alarm does not.
 *
 * The inexact fallback is kept for a device that refuses exact alarms, but it is
 * a poor wake alarm: `setWindow` can drift, and drifting is exactly what an alarm
 * must not do. The settings screen says so rather than letting it be a surprise.
 */
class WakeAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    fun schedule(at: Instant) {
        val manager = alarmManager ?: return
        val fireAt = at.toEpochMilliseconds()
        val operation = ringingIntent()

        if (canScheduleExact(manager)) {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, showIntent()), operation)
        } else {
            manager.setWindow(AlarmManager.RTC_WAKEUP, fireAt, 5 * 60 * 1000L, operation)
        }
    }

    fun cancel() {
        alarmManager?.cancel(ringingIntent())
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun ringingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            WakeAlarmContract.REQUEST_CODE,
            Intent(context, WakeAlarmReceiver::class.java)
                .setAction(WakeAlarmContract.ACTION_RING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** What the status bar's alarm entry opens. */
    private fun showIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            WakeAlarmContract.REQUEST_CODE,
            launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/**
 * The wake alarm going off, and the two buttons coming back from it.
 *
 * A receiver cannot start an activity from the background on Android 10 and
 * later, which is why ringing goes through a **full-screen-intent notification**
 * rather than a direct `startActivity`. That is the sanctioned path for an alarm,
 * and it degrades honestly: where the system declines to open the screen, the
 * notification is still there and still sounds.
 */
class WakeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ErikApplication ?: return
        val coordinator = app.container.wakeAlarmCoordinator

        when (intent.action) {
            WakeAlarmContract.ACTION_SNOOZE -> coordinator.snooze()

            WakeAlarmContract.ACTION_STOP -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        coordinator.stop()
                    } finally {
                        pending.finish()
                    }
                }
            }

            WakeAlarmContract.ACTION_RING -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        coordinator.onRing()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
