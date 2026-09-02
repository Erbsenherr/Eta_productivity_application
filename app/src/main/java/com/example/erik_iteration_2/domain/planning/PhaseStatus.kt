package com.example.erik_iteration_2.domain.planning

import kotlinx.datetime.LocalDateTime

/**
 * What the daily phase still owes, as the dashboard reports it.
 *
 * Two duties, not one: settle today, and have tomorrow planned. They are tracked
 * apart because planning ahead is encouraged — doing tomorrow at lunchtime must
 * not make the evening's settlement look done.
 */
data class DailyPhaseStatus(
    val todaySettled: Boolean,
    val tomorrowConfirmed: Boolean,
    val tomorrowHasBlocks: Boolean,
    val dueAt: LocalDateTime?,
) {
    val isComplete: Boolean get() = todaySettled && tomorrowConfirmed

    /** Tomorrow already has a plan waiting to be waved through. */
    val tomorrowReadyToConfirm: Boolean get() = !tomorrowConfirmed && tomorrowHasBlocks

    /** The one sentence the dashboard leads with. */
    val headline: String
        get() = when {
            isComplete -> "Alles erledigt."
            !todaySettled && tomorrowConfirmed -> "Heute ist noch nicht abgerechnet."
            todaySettled && tomorrowReadyToConfirm -> "Morgen ist geplant, aber noch nicht bestätigt."
            todaySettled -> "Morgen ist noch nicht geplant."
            tomorrowReadyToConfirm -> "Morgen ist vorgeplant — heute fehlt noch der Abschluss."
            else -> "Tagesabschluss steht aus."
        }
}
