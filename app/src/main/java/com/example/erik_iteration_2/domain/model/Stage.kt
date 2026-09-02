package com.example.erik_iteration_2.domain.model

/**
 * Sammelliste/Sperrliste/Wochenliste/Tagesliste/Erfolgsliste.
 *
 * "Liste für Morgen" is not a Stage: it is tomorrow's [PlannedBlock]s once that
 * day's [DayPlan] is confirmed.
 */
enum class Stage {
    COLLECTION,
    LOCKED,
    WEEK,
    DAY,
    DONE,
}
