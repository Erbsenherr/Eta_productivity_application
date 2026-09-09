package com.example.erik_iteration_2.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.net.Uri
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.example.erik_iteration_2.R
import com.example.erik_iteration_2.domain.planning.PlanningPhase

/**
 * A channel's sound is fixed when it is created and cannot be changed afterwards,
 * so giving the alarm its own sound meant a **new channel id**. The old one is
 * deleted with it, or every existing install would keep a dead "Planungsphasen"
 * entry in the system settings next to the live one.
 */
private const val CHANNEL_ID = "planning_v2"
private const val LEGACY_CHANNEL_ID = "planning"

/** One id per phase, so the daily and weekly alarms never overwrite each other. */
private fun notificationId(phase: PlanningPhase) = 2000 + phase.ordinal

/**
 * The alarm's face.
 *
 * Three actions, matching what `Planungsphase.md` asks for: get on with it, five
 * more minutes, or the emergency deferral. The deferral opens the app rather than
 * guessing a number of hours — the concept calls it a manual entry, and a
 * notification button cannot ask how many.
 */
object PlanningNotifications {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager: NotificationManager = context.getSystemService() ?: return

        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Planungsphasen",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Erinnert an die Tages- und Wochenplanung."
                setSound(
                    soundUri(context),
                    AudioAttributes.Builder()
                        // On the alarm stream, like the alarm it belongs to: this
                        // one is meant to interrupt an evening, and it carries
                        // `setAlarmClock` on the other side.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
    }

    /** The user's own `planning_start.mp3`, from `res/raw`. */
    private fun soundUri(context: Context): Uri =
        "android.resource://${context.packageName}/${R.raw.planning_start}".toUri()

    fun show(context: Context, phase: PlanningPhase) {
        ensureChannel(context)

        val title = when (phase) {
            PlanningPhase.DAILY -> "Zeit für die Tagesplanung"
            PlanningPhase.WEEKLY -> "Zeit für die Wochenplanung"
        }
        val text = when (phase) {
            PlanningPhase.DAILY -> "Tag abschließen und morgen planen."
            PlanningPhase.WEEKLY -> "Rückblick, Inflation und die kommende Woche."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // Ignored from Android 8 on, where the channel above carries the
            // sound. It is what makes 7.x — which this app still supports — hear
            // anything at all.
            .setSound(soundUri(context), AudioManager.STREAM_ALARM)
            .setAutoCancel(true)
            // Both land *in* the phase rather than on the dashboard: an alarm that
            // says "time to plan" and then drops the user somewhere else has made
            // them do the navigating it interrupted them for.
            .setContentIntent(openIntent(context, phase, defer = false))
            .addAction(0, "Los", openIntent(context, phase, defer = false))
            .addAction(0, "5 Min", snoozeIntent(context, phase))
            .addAction(0, "NOTFALL", openIntent(context, phase, defer = true))
            .build()

        // Posting without the runtime permission throws on API 33+; the alarm still
        // reschedules itself, so a refused permission costs the reminder, not the loop.
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(phase), notification)
        }
    }

    fun dismiss(context: Context, phase: PlanningPhase) {
        NotificationManagerCompat.from(context).cancel(notificationId(phase))
    }

    private fun openIntent(context: Context, phase: PlanningPhase, defer: Boolean): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId(phase) * 10 + if (defer) 1 else 0,
            launchIntent(context).apply {
                if (defer) {
                    putExtra(PlanningAlarmContract.EXTRA_DEFER_PHASE, phase.name)
                } else {
                    putExtra(PlanningAlarmContract.EXTRA_OPEN_PHASE, phase.name)
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun snoozeIntent(context: Context, phase: PlanningPhase): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            notificationId(phase) * 10 + 2,
            Intent(context, PlanningAlarmReceiver::class.java).apply {
                action = PlanningAlarmContract.ACTION_SNOOZE
                putExtra(PlanningAlarmContract.EXTRA_PHASE, phase.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
