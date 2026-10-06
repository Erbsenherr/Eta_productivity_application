package com.example.eta

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.eta.alarm.PlanningNotifications
import com.example.eta.alarm.ReminderCoordinator
import com.example.eta.alarm.TaskEndNotifications
import com.example.eta.alarm.TaskNudgeNotifications
import com.example.eta.alarm.TaskStartNotifications
import com.example.eta.alarm.WakeAlarmNotifications
import com.example.eta.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EtaApplication : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * Whether a screen of this app is in front.
     *
     * Counted here rather than pulled from a lifecycle library: the alarm needs
     * exactly this one fact — `Planungsphase.md` keeps quiet while the user
     * already has the app open — and counting started activities answers it
     * without another dependency.
     */
    private var startedActivities = 0

    val isInForeground: Boolean get() = startedActivities > 0

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, { isInForeground })

        PlanningNotifications.ensureChannel(this)
        TaskStartNotifications.ensureChannel(this)
        TaskEndNotifications.ensureChannel(this)
        TaskNudgeNotifications.ensureChannels(this)
        ReminderCoordinator.ensureChannel(this)
        WakeAlarmNotifications.ensureChannel(this)
        registerActivityLifecycleCallbacks(ForegroundCounter())

        // Alarms do not survive an uninstall-reinstall or a cleared app, and a
        // setup answered on another install would otherwise never ring again.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            container.planningAlarmCoordinator.rescheduleAll()
            container.taskStartCoordinator.reschedule()
            container.wakeAlarmCoordinator.reschedule()
            container.reminderCoordinator.reschedule()
        }
    }

    private inner class ForegroundCounter : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
