package com.example.eta.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.example.eta.EtaApplication
import com.example.eta.R
import com.example.eta.data.repository.ReminderRepository
import com.example.eta.data.repository.TaskReminderService
import com.example.eta.domain.model.Reminder
import com.example.eta.domain.reminder.nextReminderAlarm
import com.example.eta.ui.format.formatClock
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object ReminderAlarmContract {
    const val ACTION_RING = "com.example.eta.REMINDER_RING"
    const val REQUEST_CODE = 1200
}

private const val CHANNEL_ID = "reminders"
private const val NOTIFICATION_ID = 5000

/**
 * The reminders from the Erinnerungen tab, from the list to the ringing.
 *
 * The same shape as the task alarm: **one** alarm, always aimed at the soonest
 * reminder still to ring, and re-aimed on every path — app start, boot, every
 * edit on the tab, every ring. A reminder that fires and forgets to arm the next
 * would silence every one after it, and nothing would say so.
 *
 * What rings is read off the database at that moment, not carried in the intent:
 * everything due and not yet rung goes out together. That is also what makes a
 * reminder the phone slept through — switched off, or doze — ring as soon as the
 * app is next woken rather than never.
 */
class ReminderCoordinator(
    private val context: Context,
    private val repository: ReminderRepository,
    /**
     * The reminders tasks owe, reconciled before anything is aimed.
     *
     * Here rather than on its own schedule because the set of moments this is
     * needed at is exactly the set this coordinator already reschedules on: app
     * start, boot, every edit on the tab, every exit from a flow, every ring.
     * Nothing has to remember to call it separately, which is the only version
     * of this that stays true.
     */
    private val taskReminders: TaskReminderService? = null,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    suspend fun reschedule() {
        val manager = alarmManager ?: return
        runCatching { taskReminders?.sync() }
        val next = nextReminderAlarm(repository.findPending(), clock.now())
        if (next == null) {
            manager.cancel(ringingIntent())
            return
        }
        val fireAt = next.toEpochMilliseconds()
        if (manager.canScheduleExact()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, ringingIntent())
        } else {
            manager.setWindow(AlarmManager.RTC_WAKEUP, fireAt, 5 * 60 * 1000L, ringingIntent())
        }
    }

    /**
     * Everything due goes out, is marked as rung, and the next one is armed.
     *
     * [reschedule] at the end is what puts a task's *next* reminder in place: the
     * one that just rang is marked, so the sync inside it finds the task owing
     * one for the following occurrence.
     */
    suspend fun onRing() {
        val due = repository.markDueAsFired(clock.now())
        if (due.isNotEmpty()) {
            EtaSound.play(context, R.raw.notification)
            due.forEach { show(it) }
        }
        reschedule()
    }

    private fun show(reminder: Reminder) {
        ensureChannel(context)
        val time = reminder.at.toLocalDateTime(timeZone).time.formatClock()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Erinnerung · $time")
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    reminder.id.hashCode(),
                    launchIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        postNotification(context, reminder.id, NOTIFICATION_ID, notification)
    }

    private fun ringingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ReminderAlarmContract.REQUEST_CODE,
            Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ReminderAlarmContract.ACTION_RING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        fun ensureChannel(context: Context) = ensureSilentChannel(
            context = context,
            id = CHANNEL_ID,
            name = "Erinnerungen",
            description = "Die Erinnerungen, die du im Reiter Erinnerungen stellst.",
        )
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderAlarmContract.ACTION_RING) return
        val app = context.applicationContext as? EtaApplication ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                app.container.reminderCoordinator.onRing()
            } finally {
                pending.finish()
            }
        }
    }
}
