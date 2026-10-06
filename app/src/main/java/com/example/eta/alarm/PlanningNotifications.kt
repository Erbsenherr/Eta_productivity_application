package com.example.eta.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.eta.R
import com.example.eta.domain.planning.PlanningPhase

/**
 * A channel's sound is fixed when it is created and cannot be changed afterwards,
 * so every change to it has meant a **new channel id**: `planning_v2` gave the
 * alarm its own sound, `planning_v3` took the sound off the channel again so that
 * [EtaSound] can play it. The old ids are deleted with it, or every existing
 * install would keep dead "Planungsphasen" entries next to the live one.
 */
private const val CHANNEL_ID = "planning_v3"
private val LEGACY_CHANNEL_IDS = listOf("planning", "planning_v2")

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

    fun ensureChannel(context: Context) = ensureSilentChannel(
        context = context,
        id = CHANNEL_ID,
        name = "Planungsphasen",
        description = "Erinnert an die Tages- und Wochenplanung.",
        legacyIds = LEGACY_CHANNEL_IDS,
    )

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
            .setAutoCancel(true)
            // Both land *in* the phase rather than on the dashboard: an alarm that
            // says "time to plan" and then drops the user somewhere else has made
            // them do the navigating it interrupted them for.
            .setContentIntent(openIntent(context, phase, defer = false))
            .addAction(0, "Los", openIntent(context, phase, defer = false))
            .addAction(0, "5 Min", snoozeIntent(context, phase))
            .addAction(0, "NOTFALL", openIntent(context, phase, defer = true))
            .build()

        // A refused permission costs the banner, not the loop: the alarm still
        // reschedules itself, and the sound has already been played.
        postNotification(context, null, notificationId(phase), notification)
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
