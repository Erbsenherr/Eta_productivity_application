package com.example.erik_iteration_2.domain.model

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.UUID

/** What happens to one recurring task while the holiday lasts. */
enum class VacationTreatment {
    /** It simply does not happen. */
    SUSPEND,

    /** It happens, at a different time of day. */
    MOVE,
}

/**
 * A stretch of days in which the standing schedule steps aside.
 *
 * Both dates inclusive: a holiday from the 1st to the 7th is seven days, which is
 * how anyone would say it.
 */
@Entity(tableName = "vacations")
data class Vacation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val label: String,
    val from: LocalDate,
    val to: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun covers(date: LocalDate): Boolean = date >= from && date <= to

    /** Over once its last day has passed; the ordinary schedule then returns. */
    fun hasEnded(today: LocalDate): Boolean = today > to
}

/**
 * What one recurring definition does during one holiday.
 *
 * The decision belongs to the **definition**, not to individual occurrences.
 * Suppressing a holiday by deleting its blocks does not hold: `expandRecurring`
 * skips only the item/date pairs that are currently materialized, so a deleted
 * future block comes back the next time a planning phase covers that date. A
 * stored decision that expansion consults is the only version that stays.
 */
@Entity(
    tableName = "vacation_rules",
    indices = [Index("vacationId"), Index("itemId")],
)
data class VacationRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vacationId: String,
    val itemId: String,
    val treatment: VacationTreatment,
    /**
     * Where a moved task goes. Only meaningful for [VacationTreatment.MOVE], and
     * an override rather than an edit: `recurrenceRule` and `startTime` are left
     * alone, so the ordinary schedule is simply what returns afterwards — nothing
     * has to be restored.
     */
    val movedStart: LocalTime? = null,
)
