package com.example.erik_iteration_2.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.example.erik_iteration_2.R
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.planning.endMinute
import com.example.erik_iteration_2.domain.planning.minuteToLocalTime
import com.example.erik_iteration_2.ui.format.formatClock

private const val CHANNEL_ID = "task_end"

/** One id, one tag per block: several tasks may end on the same minute. */
private const val NOTIFICATION_ID = 3100

/**
 * "The time you gave this is up."
 *
 * The counterpart to [TaskStartNotifications], and it takes the same stream for
 * the same reason: this is a nudge about the world, not an alarm, so a phone that
 * has been silenced stays silent for it.
 *
 * It only ever fires for a block that is still **open** — one already ticked off
 * has been dealt with, and the user does not need to be told what they told the
 * app. That, and `Item.endSound`, are the two gates; the coordinator applies both.
 *
 * Its own channel rather than the start one, because a channel's sound is fixed
 * when the channel is created — the same trap `planning_v2` documents.
 */
object TaskEndNotifications {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager: NotificationManager = context.getSystemService() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Ende einer Aufgabe",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Meldet sich, wenn die geplante Zeit einer Aufgabe abgelaufen ist."
                setSound(
                    soundUri(context),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
    }

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
            // Ignored from Android 8 on, where the channel carries the sound.
            .setSound(soundUri(context), AudioManager.STREAM_NOTIFICATION)
            .build()

        // Same swallow as everywhere else: posting without the runtime permission
        // throws on API 33+, and a refused permission must cost the announcement,
        // never the alarm that books the next one.
        runCatching {
            NotificationManagerCompat.from(context)
                .notify("end:${entry.block.id}", NOTIFICATION_ID, notification)
        }
    }

    /** The user's own `task_end.mp3`, from `res/raw`. */
    private fun soundUri(context: Context): Uri =
        "android.resource://${context.packageName}/${R.raw.task_end}".toUri()

    private fun openIntent(context: Context, entry: BlockWithItem): PendingIntent =
        PendingIntent.getActivity(
            context,
            ("end:" + entry.block.id).hashCode(),
            launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
