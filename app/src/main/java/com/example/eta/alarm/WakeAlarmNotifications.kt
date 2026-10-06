package com.example.eta.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.example.eta.R

private const val CHANNEL_ID = "wake"
private const val NOTIFICATION_ID = 4000

/**
 * The wake alarm's face.
 *
 * A **full-screen intent**, because a receiver may not start an activity from the
 * background on Android 10 and later. This is the path the system keeps open for
 * alarms: where it is allowed, [WakeAlarmActivity] comes up over the lock screen;
 * where it is not, the notification is still posted and still sounds, so the
 * alarm degrades to a loud heads-up rather than to nothing.
 *
 * The channel is **silent on purpose**. The sound is played by the activity, in a
 * loop, on the alarm stream — a channel sound plays once and stops, which is a
 * notification, not an alarm. Two sources would talk over each other.
 */
object WakeAlarmNotifications {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager: NotificationManager = context.getSystemService() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Weckruf",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Der Weckruf zur eingestellten Aufstehzeit."
                setSound(null, null)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
        )
    }

    fun show(context: Context) {
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Aufstehen")
            .setContentText("Der Tag fängt an.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Not dismissible by a swipe: an alarm that can be wiped away half
            // asleep without answering it is not an alarm.
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(ringingScreen(context), true)
            .setContentIntent(ringingScreen(context))
            .addAction(0, "Schlummern", action(context, WakeAlarmContract.ACTION_SNOOZE, 1))
            .addAction(0, "Aus", action(context, WakeAlarmContract.ACTION_STOP, 2))
            .build()

        postNotification(context, null, NOTIFICATION_ID, notification)
    }

    fun dismiss(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * What the alarm actually rings with.
     *
     * The device's own alarm tone rather than one of the app's mp3s: it is what
     * the user already recognises as an alarm, it is designed to be looped, and
     * it is what their volume keys are aimed at. Swapping in a file of their own
     * is one line — `"android.resource://…/${R.raw.some_file}".toUri()`, with the
     * mp3 copied into `res/raw` the way `task_start` and `planning_start` are.
     */
    fun soundUri(context: Context): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /** Alarm stream, so the ringer being silenced does not silence the alarm. */
    val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun ringingScreen(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, WakeAlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID + code,
            Intent(context, WakeAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
