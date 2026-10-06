package com.example.eta.alarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Whether this device will actually let Eta's alarms through.
 *
 * The reason this exists: every one of these can be off, each of them silences a
 * different part of the app, and **none of them produces an error**. A refused
 * notification permission makes the planning alarm fire, reschedule and show
 * nothing. A denied exact-alarm permission turns every alarm into a window that
 * doze may push past its hour. An unexempted app can be stopped outright by the
 * manufacturer's battery manager. The result in all three cases is the same from
 * the outside — no alarm, no explanation — so the app has to be able to say which
 * one it is.
 */
data class AlarmReadiness(
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
    val ignoringBatteryOptimisation: Boolean,
) {
    val allGood: Boolean
        get() = notificationsAllowed && exactAlarmsAllowed && ignoringBatteryOptimisation
}

/**
 * Whether an exact alarm will be honoured. Before Android 12 there was nothing
 * to refuse; from then on the system has to be asked.
 */
internal fun AlarmManager.canScheduleExact(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()

fun alarmReadiness(context: Context): AlarmReadiness = AlarmReadiness(
    notificationsAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    },
    exactAlarmsAllowed = context.getSystemService<AlarmManager>()?.canScheduleExact() ?: false,
    ignoringBatteryOptimisation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } else {
        true
    },
)

/**
 * The system screens that can put each of the three right.
 *
 * Deliberately the *settings* screens rather than a runtime prompt: two of these
 * have no prompt at all, and the notification one may already have been dismissed
 * often enough that Android will not ask again. Sending the user to the switch is
 * the only route that always works.
 */
object AlarmSettingsIntents {

    fun notifications(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            appDetails(context)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun exactAlarms(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData("package:${context.packageName}".toAndroidUri())
        } else {
            appDetails(context)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * The list, not the "please exempt me" prompt.
     *
     * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` needs a permission that app
     * stores treat as a red flag, and it is not needed: the list screen lets the
     * user do the same thing in one more tap.
     */
    fun batteryOptimisation(context: Context): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:${context.packageName}".toAndroidUri())

    private fun String.toAndroidUri() = android.net.Uri.parse(this)
}
