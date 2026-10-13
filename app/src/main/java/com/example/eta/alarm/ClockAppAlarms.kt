package com.example.eta.alarm

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.example.eta.domain.setup.ClockAlarm
import kotlinx.datetime.DayOfWeek

/**
 * The clock apps an alarm can be handed to.
 *
 * [SYSTEM] names none and lets the phone pick its own — on a Samsung or a
 * Xiaomi that is the maker's clock, which is the one their battery saving
 * leaves alone. The two named ones are the user's request: Google's clock, and
 * the plain Android one a phone without Google services carries.
 */
enum class ClockApp(val label: String, val packageName: String?) {
    SYSTEM("Standard-Uhr des Handys", null),
    GOOGLE("Google Uhr", "com.google.android.deskclock"),
    ANDROID("Android-Uhr", "com.android.deskclock"),
}

/**
 * Setting the morning alarm in the phone's clock app instead of ringing it here.
 *
 * Through `AlarmClock.ACTION_SET_ALARM`, the one door Android has for this. What
 * it can do is **create** an alarm — repeating on given weekdays, labelled,
 * without showing the clock's own screen. What it cannot do is the other half:
 * there is no way to change or delete an alarm afterwards, nor to ask which
 * exist. So this is a button that is pressed, not a setting that is kept in
 * step: after the hours of getting up change, the old alarms have to be deleted
 * in the clock app by hand and these set again — and the screen says so.
 *
 * For the same reason a night changed for one date (step 40) is not carried
 * over. A repeating alarm knows weekdays, not dates.
 */
object ClockAppAlarms {

    /** The apps that would take an alarm on this phone, [ClockApp.SYSTEM] first. */
    fun available(context: Context): List<ClockApp> =
        ClockApp.entries.filter { intentFor(it).resolveActivity(context.packageManager) != null }

    fun chosen(context: Context): ClockApp {
        val stored = prefs(context).getString(KEY_APP, null)
        return ClockApp.entries.firstOrNull { it.name == stored } ?: ClockApp.SYSTEM
    }

    fun choose(context: Context, app: ClockApp) {
        prefs(context).edit().putString(KEY_APP, app.name).apply()
    }

    /**
     * Hands [alarms] to [app], all in one call.
     *
     * One `startActivities` rather than a start per alarm: the first start
     * puts the clock app in front, and an app that is no longer in front may
     * not start anything further. False when no clock app answered.
     */
    fun setUp(context: Context, alarms: List<ClockAlarm>, app: ClockApp): Boolean {
        if (alarms.isEmpty()) return false
        val intents = alarms.map { alarm ->
            intentFor(app).apply {
                putExtra(AlarmClock.EXTRA_HOUR, alarm.time.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, alarm.time.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Eta · Aufstehen")
                putExtra(AlarmClock.EXTRA_VIBRATE, true)
                // Without the clock's own screen: several are being made at once.
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                putIntegerArrayListExtra(
                    AlarmClock.EXTRA_DAYS,
                    ArrayList(alarm.days.sortedBy { it.ordinal }.map { it.toCalendarDay() }),
                )
            }
        }
        return runCatching { context.startActivities(intents.toTypedArray()) }.isSuccess
    }

    private fun intentFor(app: ClockApp): Intent =
        Intent(AlarmClock.ACTION_SET_ALARM).apply { app.packageName?.let(::setPackage) }

    /** `java.util.Calendar`'s numbering, which starts the week on Sunday at 1. */
    private fun DayOfWeek.toCalendarDay(): Int = (ordinal + 1) % 7 + 1

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("clock", Context.MODE_PRIVATE)

    private const val KEY_APP = "app"
}
