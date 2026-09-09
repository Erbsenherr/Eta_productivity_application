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

/** Intent keys the task-start alarm passes to itself. */
object TaskStartAlarmContract {
    const val ACTION_RING = "com.example.erik_iteration_2.TASK_START_RING"

    /**
     * The minute this alarm was laid down for, in epoch millis.
     *
     * Carried rather than re-derived from the clock: an inexact alarm can arrive
     * ten minutes late, and "which blocks were meant to start" has to survive
     * that. Comparing against the intended minute answers it exactly; comparing
     * against `now` would announce the wrong thing or nothing at all.
     */
    const val EXTRA_AT = "at"

    const val REQUEST_CODE = 1100
}

/**
 * One alarm, always pointing at the next block that has yet to begin.
 *
 * Deliberately **not** `setAlarmClock`, unlike the planning phases: that puts an
 * entry in the status bar's alarm slot, and a day of ten blocks would keep
 * rewriting it into noise. This one wants to be exact and to survive doze, which
 * is what `setExactAndAllowWhileIdle` is for, with the same inexact fallback for
 * a user who has refused the exact-alarm permission.
 */
class TaskStartAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    fun schedule(at: Instant) {
        val manager = alarmManager ?: return
        val fireAt = at.toEpochMilliseconds()
        val operation = ringingIntent(at)

        if (canScheduleExact(manager)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation)
        } else {
            // Five minutes, tighter than the planning alarm's window: a nudge that
            // something is starting is worth less the later it comes.
            manager.setWindow(AlarmManager.RTC_WAKEUP, fireAt, 5 * 60 * 1000L, operation)
        }
    }

    fun cancel() {
        val manager = alarmManager ?: return
        // A cancel has to build the same PendingIntent the schedule did, which is
        // why the payload lives in an extra and not in the request code.
        manager.cancel(
            PendingIntent.getBroadcast(
                context,
                TaskStartAlarmContract.REQUEST_CODE,
                Intent(context, TaskStartAlarmReceiver::class.java)
                    .setAction(TaskStartAlarmContract.ACTION_RING),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun ringingIntent(at: Instant): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            TaskStartAlarmContract.REQUEST_CODE,
            Intent(context, TaskStartAlarmReceiver::class.java).apply {
                action = TaskStartAlarmContract.ACTION_RING
                putExtra(TaskStartAlarmContract.EXTRA_AT, at.toEpochMilliseconds())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/**
 * A block's minute arriving.
 *
 * `goAsync`, like the planning receiver and for the same reason: announcing and
 * then booking the next one both need the database, and the booking is the half
 * that must not be lost — one dropped reschedule silences the rest of the day.
 */
class TaskStartAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskStartAlarmContract.ACTION_RING) return
        val app = context.applicationContext as? ErikApplication ?: return
        val at = intent.getLongExtra(TaskStartAlarmContract.EXTRA_AT, 0L)

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                app.container.taskStartCoordinator.onRing(Instant.fromEpochMilliseconds(at))
            } finally {
                pending.finish()
            }
        }
    }
}
