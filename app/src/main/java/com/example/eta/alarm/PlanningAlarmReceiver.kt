package com.example.eta.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.eta.EtaApplication
import com.example.eta.domain.planning.PlanningPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The alarm arriving, and the snooze coming back from the notification.
 *
 * Both need the database, so both run in `goAsync` — a receiver that returned
 * before its coroutine finished would be killed mid-query, and the reschedule is
 * exactly the part that must not be lost.
 */
class PlanningAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val phase = intent.getStringExtra(PlanningAlarmContract.EXTRA_PHASE)
            ?.let { runCatching { PlanningPhase.valueOf(it) }.getOrNull() }
            ?: return

        val app = context.applicationContext as? EtaApplication ?: return
        val coordinator = app.container.planningAlarmCoordinator

        when (intent.action) {
            PlanningAlarmContract.ACTION_SNOOZE -> coordinator.snooze(phase)

            PlanningAlarmContract.ACTION_RING -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        coordinator.onRing(phase)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

/** Alarms do not survive a reboot, so they are laid down again afterwards. */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? EtaApplication ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                app.container.planningAlarmCoordinator.rescheduleAll()
                app.container.taskStartCoordinator.reschedule()
                app.container.wakeAlarmCoordinator.reschedule()
                app.container.reminderCoordinator.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
