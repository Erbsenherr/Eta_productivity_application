package com.example.erik_iteration_2.domain.model

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Confirmation state of one day's plan.
 *
 * Once confirmed, that day's blocks show up read-only as the "Liste für Morgen".
 * Long-pressing that screen clears [confirmedAt], which brings the revolver back
 * out and reopens editing.
 */
@Entity(tableName = "day_plans")
data class DayPlan(
    @PrimaryKey val date: LocalDate,
    val confirmedAt: Instant? = null,
    /**
     * When the evening reevaluation settled this day.
     *
     * Separate from [confirmedAt] because the two are different promises about
     * different days: confirming says "tomorrow is planned", settling says "today
     * is accounted for". Reading one as the other let a day be planned ahead and
     * never settled — the alarm went quiet and the harvest was silently lost.
     */
    val settledAt: Instant? = null,
) {
    @get:Ignore
    val isConfirmed: Boolean get() = confirmedAt != null

    @get:Ignore
    val isSettled: Boolean get() = settledAt != null
}
