package com.example.eta.data.local

import androidx.room3.ColumnTypeConverter
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.setup.DailySlot
import com.example.eta.domain.setup.HousekeepingPlan
import com.example.eta.domain.setup.MealPlan
import com.example.eta.domain.setup.MindfulnessPlan
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.WeeklySlot
import com.example.eta.domain.setup.WorkSchedule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Enums of our own are handled by Room itself; these cover the time types and
 * every value object that encodes itself to a single column.
 *
 * [DayOfWeek] is converted by hand rather than left to Room's enum support: it is
 * `java.time.DayOfWeek` behind the typealias and arrives through desugaring, so
 * spelling out the mapping keeps it independent of how that resolves.
 */
class Converters {

    @ColumnTypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilliseconds()

    @ColumnTypeConverter
    fun epochMillisToInstant(value: Long?): Instant? =
        value?.let { Instant.fromEpochMilliseconds(it) }

    @ColumnTypeConverter
    fun localDateToIso(value: LocalDate?): String? = value?.toString()

    @ColumnTypeConverter
    fun isoToLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @ColumnTypeConverter
    fun localTimeToSecondOfDay(value: LocalTime?): Int? = value?.toSecondOfDay()

    @ColumnTypeConverter
    fun secondOfDayToLocalTime(value: Int?): LocalTime? =
        value?.let { LocalTime.fromSecondOfDay(it) }

    @ColumnTypeConverter
    fun durationToSeconds(value: Duration?): Long? = value?.inWholeSeconds

    @ColumnTypeConverter
    fun secondsToDuration(value: Long?): Duration? = value?.seconds

    @ColumnTypeConverter
    fun recurrenceRuleToString(value: RecurrenceRule?): String? = value?.encode()

    @ColumnTypeConverter
    fun stringToRecurrenceRule(value: String?): RecurrenceRule? =
        value?.let { RecurrenceRule.decode(it) }

    @ColumnTypeConverter
    fun dayOfWeekToName(value: DayOfWeek?): String? = value?.name

    @ColumnTypeConverter
    fun nameToDayOfWeek(value: String?): DayOfWeek? = value?.let { DayOfWeek.valueOf(it) }

    /** In week order, so the same set is always the same string. */
    @ColumnTypeConverter
    fun weekdaySetToString(value: Set<DayOfWeek>?): String? =
        value?.let { days -> DayOfWeek.entries.filter { it in days }.joinToString(",") { it.name } }

    @ColumnTypeConverter
    fun stringToWeekdaySet(value: String?): Set<DayOfWeek>? =
        value?.split(',')?.filter { it.isNotEmpty() }?.map(DayOfWeek::valueOf)?.toSet()

    @ColumnTypeConverter
    fun weeklySlotToString(value: WeeklySlot?): String? = value?.encode()

    @ColumnTypeConverter
    fun stringToWeeklySlot(value: String?): WeeklySlot? = value?.let { WeeklySlot.decode(it) }

    @ColumnTypeConverter
    fun dailySlotToString(value: DailySlot?): String? = value?.encode()

    @ColumnTypeConverter
    fun stringToDailySlot(value: String?): DailySlot? = value?.let { DailySlot.decode(it) }

    @ColumnTypeConverter
    fun mealPlanToString(value: MealPlan?): String? = value?.encode()

    @ColumnTypeConverter
    fun stringToMealPlan(value: String?): MealPlan? = value?.let { MealPlan.decode(it) }

    @ColumnTypeConverter
    fun housekeepingToString(value: HousekeepingPlan?): String? = value?.encode()

    @ColumnTypeConverter
    fun stringToHousekeeping(value: String?): HousekeepingPlan? =
        value?.let { HousekeepingPlan.decode(it) }

    @ColumnTypeConverter
    fun mindfulnessToString(value: MindfulnessPlan?): String? = value?.encode()

    @ColumnTypeConverter
    fun stringToMindfulness(value: String?): MindfulnessPlan? =
        value?.let { MindfulnessPlan.decode(it) }

    @ColumnTypeConverter
    fun nightTimesToString(value: NightTimes?): String? = value?.encode()

    @ColumnTypeConverter
    fun stringToNightTimes(value: String?): NightTimes? = value?.let { NightTimes.decode(it) }

    @ColumnTypeConverter
    fun workScheduleToString(value: WorkSchedule?): String? = value?.encode()

    @ColumnTypeConverter
    fun stringToWorkSchedule(value: String?): WorkSchedule? =
        value?.let { WorkSchedule.decode(it) }
}
