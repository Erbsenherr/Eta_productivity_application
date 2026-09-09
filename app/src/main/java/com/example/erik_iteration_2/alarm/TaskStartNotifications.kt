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
import com.example.erik_iteration_2.domain.planning.notesInOrder
import com.example.erik_iteration_2.ui.format.formatClock

private const val CHANNEL_ID = "task_start"

/** One id, one tag per block: several tasks may start on the same minute. */
private const val NOTIFICATION_ID = 3000

/**
 * "This starts now."
 *
 * A separate channel from the planning alarm, and deliberately on a different
 * stream. The planning alarm is an alarm — it interrupts an evening on purpose
 * and rides the alarm stream. This is a nudge about the world, so it takes the
 * notification stream: a phone that has been silenced should stay silent for it,
 * which would be the wrong answer for the alarm and is the right one here.
 */
object TaskStartNotifications {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager: NotificationManager = context.getSystemService() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Beginn einer Aufgabe",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Meldet sich, wenn laut Plan etwas Neues anfängt."
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
            // Ignored from Android 8 on, where the channel carries the sound.
            .setSound(soundUri(context), AudioManager.STREAM_NOTIFICATION)
            .build()

        // Same swallow as the planning notification: posting without the runtime
        // permission throws on API 33+, and a refused permission must cost the
        // announcement, never the alarm that books the next one.
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(entry.block.id, NOTIFICATION_ID, notification)
        }
    }

    /** The user's own `task_start.mp3`, from `res/raw`. */
    private fun soundUri(context: Context): Uri =
        "android.resource://${context.packageName}/${R.raw.task_start}".toUri()

    private fun openIntent(context: Context, entry: BlockWithItem): PendingIntent =
        PendingIntent.getActivity(
            context,
            entry.block.id.hashCode(),
            launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
