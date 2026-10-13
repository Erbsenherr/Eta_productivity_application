package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import com.example.eta.domain.planning.minuteOfDay
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** How long after the hour of getting up still counts as on time. */
const val PUNCTUAL_WAKE_MINUTES = 5

/** What getting up on time, and going to bed on time, are each worth. */
const val PUNCTUAL_SLEEP_POINTS = 0.5

/**
 * What the user said about one day's two ends: when they got up that morning,
 * and whether they went to bed on time that evening.
 *
 * Keyed by the **calendar day the answer is about**. The morning's is plain. The
 * evening's is the day the evening belongs to, also when the bedtime itself lies
 * after midnight: Monday's row says how Monday ended, whichever side of twelve
 * that was — and its half point is booked with **Tuesday's** settlement, the day
 * that night ends on.
 *
 * Nothing here is measured. Both answers are the user's word, which is what the
 * rest of the app runs on as well; the statistics tab counts them, and a day
 * nobody answered for is left out of the count rather than read as a failure.
 */
@Entity(tableName = "sleep_logs")
data class SleepLog(
    @PrimaryKey val date: LocalDate,
    /** When the user got up, as they reported it. Null: not answered. */
    val wokeAt: LocalTime? = null,
    /**
     * The hour of getting up that applied that morning. Stored beside the
     * answer, because the settings can change afterwards and "was that on time"
     * must go on meaning what it meant then.
     */
    val wakeDue: LocalTime? = null,
    /** Whether the evening's bedtime was kept. Null: not answered. */
    val bedOnTime: Boolean? = null,
    val updatedAt: Instant,
) {
    /**
     * Up at the hour, or at most [PUNCTUAL_WAKE_MINUTES] after it. Earlier is on
     * time as well. Null while the morning has not been answered.
     */
    @get:Ignore
    val wokeOnTime: Boolean?
        get() {
            val at = wokeAt ?: return null
            val due = wakeDue ?: return null
            return at.minuteOfDay() <= due.minuteOfDay() + PUNCTUAL_WAKE_MINUTES
        }
}
