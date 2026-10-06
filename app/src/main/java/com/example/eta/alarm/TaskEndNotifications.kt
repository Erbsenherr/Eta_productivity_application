package com.example.eta.alarm

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.eta.R
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.ui.format.formatClock

/** `_v2` since the channel went silent; see [ensureSilentChannel]. */
private const val CHANNEL_ID = "task_end_v2"
private const val LEGACY_CHANNEL_ID = "task_end"

/** One id, one tag per block: several tasks may end on the same minute. */
private const val NOTIFICATION_ID = 3100

/**
 * "The time you gave this is up."
 *
 * The counterpart to [TaskStartNotifications], and silent in the same way: the
 * coordinator plays `task_end.mp3` through [EtaSound].
 *
 * It only ever fires for a block that is still **open** — one already ticked off
 * has been dealt with, and the user does not need to be told what they told the
 * app. That, and `Item.endSound`, are the two gates; the coordinator applies both.
 */
object TaskEndNotifications {

    fun ensureChannel(context: Context) = ensureSilentChannel(
        context = context,
        id = CHANNEL_ID,
        name = "Ende einer Aufgabe",
        description = "Meldet sich, wenn die geplante Zeit einer Aufgabe abgelaufen ist.",
        legacyIds = listOf(LEGACY_CHANNEL_ID),
    )

    fun show(context: Context, entry: BlockWithItem) {
        ensureChannel(context)

        val to = minuteToLocalTime(entry.block.endMinute()).formatClock()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Zeit vorbei: ${entry.item.name}")
            .setContentText("Bis $to eingeplant.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, entry))
            .build()

        // Same swallow as everywhere else: posting without the runtime permission
        // throws on API 33+, and a refused permission must cost the announcement,
        // never the alarm that books the next one.
        postNotification(context, "end:${entry.block.id}", NOTIFICATION_ID, notification)
    }

    private fun openIntent(context: Context, entry: BlockWithItem): PendingIntent =
        PendingIntent.getActivity(
            context,
            ("end:" + entry.block.id).hashCode(),
            launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
