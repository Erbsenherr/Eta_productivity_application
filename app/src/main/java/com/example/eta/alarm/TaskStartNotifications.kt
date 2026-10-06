package com.example.eta.alarm

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.eta.R
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.planning.notesInOrder
import com.example.eta.ui.format.formatClock

/** `_v2` since the channel went silent; see [ensureSilentChannel]. */
private const val CHANNEL_ID = "task_start_v2"
private const val LEGACY_CHANNEL_ID = "task_start"

/** One id, one tag per block: several tasks may start on the same minute. */
private const val NOTIFICATION_ID = 3000

/**
 * "This starts now."
 *
 * The channel is silent: `task_start.mp3` is played by [EtaSound], which the
 * coordinator calls once per ring however many tasks start on that minute. That
 * took it off the notification stream — the user wants the sound whatever the
 * notification volume says, and quiet only under Do-Not-Disturb.
 */
object TaskStartNotifications {

    fun ensureChannel(context: Context) = ensureSilentChannel(
        context = context,
        id = CHANNEL_ID,
        name = "Beginn einer Aufgabe",
        description = "Meldet sich, wenn laut Plan etwas Neues anfängt.",
        legacyIds = listOf(LEGACY_CHANNEL_ID),
    )

    fun show(context: Context, entry: BlockWithItem) {
        ensureChannel(context)

        val from = entry.block.start.formatClock()
        val to = minuteToLocalTime(entry.block.endMinute()).formatClock()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(entry.item.name)
            .setContentText(
                // The note about today comes first where there is one; it is the
                // line that says what is different about this occurrence.
                entry.notesInOrder().firstOrNull()?.let { "$from – $to · $it" } ?: "$from – $to",
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, entry))
            .build()

        // Same swallow as the planning notification: posting without the runtime
        // permission throws on API 33+, and a refused permission must cost the
        // announcement, never the alarm that books the next one.
        postNotification(context, entry.block.id, NOTIFICATION_ID, notification)
    }

    private fun openIntent(context: Context, entry: BlockWithItem): PendingIntent =
        PendingIntent.getActivity(
            context,
            entry.block.id.hashCode(),
            launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
