package com.example.eta.domain

import com.example.eta.domain.planning.WAKE_SNOOZE
import com.example.eta.domain.setup.ClockAlarm
import com.example.eta.domain.setup.NightMode
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.clockAlarms
import com.example.eta.domain.setup.nightEndingOn
import com.example.eta.domain.setup.withNightEndingOn
import com.example.eta.domain.setup.withNightMode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The alarms handed to the phone's clock app: as many as the way the nights
 * were answered.
 */
class ClockAlarmsTest {

    private val setup = UserSetup.draft(Instant.parse("2026-10-13T06:00:00Z"))
        .copy(wakeTime = LocalTime(6, 30))

    @Test
    fun `one night for every day is one alarm on all seven`() {
        val alarms = setup.withNightMode(NightMode.SAME).clockAlarms()

        assertEquals(listOf(ClockAlarm(LocalTime(6, 30), DayOfWeek.entries.toSet())), alarms)
    }

    @Test
    fun `a weekend night of its own is two alarms, the week's and the weekend's`() {
        val weekend = setup.withNightMode(NightMode.WEEKEND)
        val alarms = weekend.clockAlarms()

        assertEquals(2, alarms.size)
        assertEquals(ClockAlarm(LocalTime(6, 30), DayOfWeek.entries.toSet() - weekend.weekendDays), alarms[0])
        assertEquals(weekend.weekendDays, alarms[1].days)
        assertEquals(weekend.weekendNight?.wake, alarms[1].time)
    }

    @Test
    fun `a night per weekday is seven alarms, also where two hours coincide`() {
        val base = setup.withNightMode(NightMode.PER_DAY)
        val wednesday = base.nightEndingOn(DayOfWeek.WEDNESDAY)
        val perDay = base.withNightEndingOn(
            DayOfWeek.WEDNESDAY,
            NightTimes(wednesday.bedPrep, wednesday.sleep, LocalTime(9, 0)),
        )

        val alarms = perDay.clockAlarms()

        assertEquals(DayOfWeek.entries.map { setOf(it) }, alarms.map { it.days })
        assertEquals(LocalTime(9, 0), alarms[DayOfWeek.WEDNESDAY.ordinal].time)
    }

    @Test
    fun `snoozing the wake alarm buys ten minutes`() {
        assertEquals(10.minutes, WAKE_SNOOZE)
    }
}
