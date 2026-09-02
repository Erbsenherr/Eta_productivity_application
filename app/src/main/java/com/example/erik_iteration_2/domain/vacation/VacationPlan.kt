package com.example.erik_iteration_2.domain.vacation

import com.example.erik_iteration_2.domain.model.Vacation
import com.example.erik_iteration_2.domain.model.VacationRule
import com.example.erik_iteration_2.domain.model.VacationTreatment
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * A holiday together with what each recurring task does during it.
 *
 * A task with no rule keeps happening as usual — the holiday only changes what it
 * was told to change. Defaulting the other way would empty the whole schedule the
 * moment a date range was entered.
 */
data class VacationPlan(
    val vacation: Vacation,
    val rules: List<VacationRule>,
) {
    private val byItem: Map<String, VacationRule> = rules.associateBy { it.itemId }

    fun ruleFor(itemId: String): VacationRule? = byItem[itemId]

    /** Whether [date] falls inside this holiday. */
    fun covers(date: LocalDate): Boolean = vacation.covers(date)
}

/**
 * The rule in force for one task on one date, across however many holidays are
 * known. A list rather than a single plan because a range being expanded can
 * easily reach past the end of one holiday and into the next.
 */
private fun List<VacationPlan>.ruleOn(itemId: String, date: LocalDate): VacationRule? =
    firstOrNull { it.covers(date) }?.ruleFor(itemId)

/** Whether the task is off entirely on [date]. */
fun List<VacationPlan>.suspends(itemId: String, date: LocalDate): Boolean =
    ruleOn(itemId, date)?.treatment == VacationTreatment.SUSPEND

/**
 * The time this task starts on [date], or null to keep its usual one.
 *
 * A MOVE without a time counts as no change rather than as an error: half an
 * answer should not silently take the task off the day.
 */
fun List<VacationPlan>.startOverrideFor(itemId: String, date: LocalDate): LocalTime? {
    val rule = ruleOn(itemId, date) ?: return null
    return if (rule.treatment == VacationTreatment.MOVE) rule.movedStart else null
}
