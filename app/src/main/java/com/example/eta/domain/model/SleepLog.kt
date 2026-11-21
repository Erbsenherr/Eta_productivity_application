package com.example.eta.domain.model

import com.example.eta.domain.reward.Balancing
import com.example.eta.domain.reward.BalanceKey
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import com.example.eta.domain.planning.minuteOfDay
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** How long after the hour of getting up still counts as on time. */
const val PUNCTUAL_WAKE_MINUTES = 5

/** What getting up on time is worth — tuned under Balancing (step 58). */
val PUNCTUAL_WAKE_POINTS: Double get() = Balancing[BalanceKey.PUNCTUAL_WAKE]

/** What going to bed on time is worth. */
val PUNCTUAL_BED_POINTS: Double get() = Balancing[BalanceKey.PUNCTUAL_BED]

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
    /**
     * The minutes after [wakeDue] that counted as on time when the morning was
     * answered (step 48) — stored for the reason [wakeDue] is. Null on a row
     * from before, which counted [PUNCTUAL_WAKE_MINUTES].
     */
    val tolerance: Int? = null,
    /**
     * When the user went to bed, by their word (step 59) — "Gehe nun schlafen"
     * pressed, or an hour given the day after. Null on a row answered only
     * with "pünktlich" / "unpünktlich", as every row before step 59 was.
     */
    val bedAt: LocalTime? = null,
    /** The bedtime that applied that evening, stored for the reason [wakeDue] is. */
    val bedDue: LocalTime? = null,
    /** The tolerance the evening was answered under — [tolerance]'s twin. */
    val bedTolerance: Int? = null,
) {
    /**
     * Up at the hour, or at most [tolerance] minutes after it. Earlier is on
     * time as well. Null while the morning has not been answered.
     */
    @get:Ignore
    val wokeOnTime: Boolean?
        get() {
            val at = wokeAt ?: return null
            val due = wakeDue ?: return null
            return at.minuteOfDay() <= due.minuteOfDay() + (tolerance ?: PUNCTUAL_WAKE_MINUTES)
        }

    /** Minutes after the hour of getting up; negative when earlier. Null unanswered. */
    @get:Ignore
    val wakeLateMinutes: Int?
        get() {
            val at = wokeAt ?: return null
            val due = wakeDue ?: return null
            return at.minuteOfDay() - due.minuteOfDay()
        }

    /**
     * Minutes after the bedtime; negative when earlier. Read across midnight
     * — a bedtime of 23:30 kept at 00:15 is 45 minutes late — by taking the
     * nearer way round. Null where no hour was given.
     */
    @get:Ignore
    val bedLateMinutes: Int?
        get() {
            val at = bedAt ?: return null
            val due = bedDue ?: return null
            val diff = Math.floorMod(at.minuteOfDay() - due.minuteOfDay(), MINUTES_IN_DAY)
            return if (diff >= MINUTES_IN_DAY / 2) diff - MINUTES_IN_DAY else diff
        }

    /**
     * What this morning is worth (step 59) — see [sleepPoints]. Zero
     * unanswered: a morning nobody spoke for neither pays nor costs.
     */
    @get:Ignore
    val wakePoints: Double
        get() = wakeLateMinutes?.let {
            sleepPoints(it, tolerance ?: PUNCTUAL_WAKE_MINUTES, PUNCTUAL_WAKE_POINTS)
        } ?: 0.0

    /**
     * What this evening is worth. With an hour, graded like the morning;
     * answered only "pünktlich" — or before step 59 — the full half point or
     * nothing, as it was.
     */
    @get:Ignore
    val bedPoints: Double
        get() {
            val late = bedLateMinutes
            return when {
                late != null -> sleepPoints(late, bedTolerance ?: PUNCTUAL_WAKE_MINUTES, PUNCTUAL_BED_POINTS)
                bedOnTime == true -> PUNCTUAL_BED_POINTS
                else -> 0.0
            }
        }
}

private const val MINUTES_IN_DAY = 24 * 60

/** After the tolerance, this many minutes earn nothing and cost nothing. */
const val SLEEP_ZERO_MINUTES = 5

/** Past that, every quarter of an hour begun costs [SLEEP_LATE_PENALTY]. */
const val SLEEP_LATE_STEP_MINUTES = 15

/** Lateness is counted up to two hours after the hour, and no further. */
const val SLEEP_LATE_CAP_MINUTES = 120

/** What each quarter begun costs — tuned under Balancing. */
val SLEEP_LATE_PENALTY: Double get() = Balancing[BalanceKey.SLEEP_LATE]

/**
 * Getting up or going to bed, graded (step 59): up to [tolerance] minutes
 * late — earlier included — pays [full]; the next [SLEEP_ZERO_MINUTES] pay
 * nothing; after that every quarter of an hour **begun** costs
 * [SLEEP_LATE_PENALTY], counted up to [SLEEP_LATE_CAP_MINUTES] after the hour.
 * With the defaults: 5 → +0.5, 6–10 → 0, 11–25 → −0.5, … 116–120 and later → −4.
 */
fun sleepPoints(lateMinutes: Int, tolerance: Int, full: Double): Double {
    val free = tolerance.coerceAtLeast(0)
    if (lateMinutes <= free) return full
    val zeroUntil = free + SLEEP_ZERO_MINUTES
    if (lateMinutes <= zeroUntil) return 0.0
    val counted = minOf(lateMinutes, SLEEP_LATE_CAP_MINUTES) - zeroUntil
    if (counted <= 0) return 0.0
    val quarters = (counted + SLEEP_LATE_STEP_MINUTES - 1) / SLEEP_LATE_STEP_MINUTES
    return -quarters * SLEEP_LATE_PENALTY
}
