package com.example.eta.alarm

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.eta.R
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.planning.PomodoroPhaseKind
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.planning.pomodoroPhaseAt
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatShort

private const val STILL_ACTIVE_CHANNEL_ID = "still_active"
private const val POMODORO_CHANNEL_ID = "pomodoro"

private const val STILL_ACTIVE_NOTIFICATION_ID = 3200
private const val POMODORO_NOTIFICATION_ID = 3300

/**
 * The two things that speak up *during* a task rather than at its edges: the
 * "Bin ich noch bei der Sache?" question and the pomodoro rhythm.
 *
 * Both channels are silent — [EtaSound] plays `still_active.mp3`,
 * `pom_pause_start.mp3` and `pom_work_start.mp3` — and each gets a channel of its
 * own so the system settings can switch one off without the other.
 *
 * One notification per block for each, **replaced** rather than stacked: a
 * pomodoro in its fourth pause says "Pause" once, not four times.
 */
object TaskNudgeNotifications {

    fun ensureChannels(context: Context) {
        ensureSilentChannel(
            context = context,
            id = STILL_ACTIVE_CHANNEL_ID,
            name = "Bin ich noch bei der Sache?",
            description = "Fragt während langer Aufgaben zu zufälligen Zeiten nach.",
        )
        ensureSilentChannel(
            context = context,
            id = POMODORO_CHANNEL_ID,
            name = "Pomodoro",
            description = "Meldet den Wechsel zwischen Arbeit und Pause.",
        )
    }

    fun showStillActive(context: Context, entry: BlockWithItem) {
        ensureChannels(context)
        val until = minuteToLocalTime(entry.block.endMinute()).formatClock()
        postNotification(
            context = context,
            tag = "still:${entry.block.id}",
            id = STILL_ACTIVE_NOTIFICATION_ID,
            notification = NotificationCompat.Builder(context, STILL_ACTIVE_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Bist du noch bei der Sache?")
                .setContentText("${entry.item.name} · bis $until")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setTimeoutAfter(10 * 60 * 1000L)
                .setContentIntent(openIntent(context, "still:${entry.block.id}"))
                .build(),
        )
    }

    /** A phase of the rhythm beginning at [minuteOfDay]. */
    fun showPomodoro(
        context: Context,
        entry: BlockWithItem,
        kind: PomodoroPhaseKind,
        minuteOfDay: Int,
    ) {
        ensureChannels(context)
        val block = entry.block
        val until = block.pomodoroPhaseAt(minuteOfDay)?.untilMinute ?: block.endMinute()
        val untilText = minuteToLocalTime(until).formatClock()

        val (title, text) = when (kind) {
            PomodoroPhaseKind.PAUSE ->
                "Pomodoro: Pause" to
                    "${block.pomodoroPause?.formatShort().orEmpty()} Pause bis $untilText · ${entry.item.name}"

            PomodoroPhaseKind.WORK ->
                "Pomodoro: weiter" to "${entry.item.name} bis $untilText"
        }

        postNotification(
            context = context,
            tag = "pomodoro:${block.id}",
            id = POMODORO_NOTIFICATION_ID,
            notification = NotificationCompat.Builder(context, POMODORO_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openIntent(context, "pomodoro:${block.id}"))
                .build(),
        )
    }

    private fun openIntent(context: Context, key: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            key.hashCode(),
            launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
