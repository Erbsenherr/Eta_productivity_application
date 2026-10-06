package com.example.eta.domain.model

/**
 * What part an item plays in the day, where the app itself has to recognise it.
 *
 * A closed set on purpose: this is the app's own vocabulary, not the user's — the
 * rules key off it, and `Category` already covers the classification the user
 * makes. `Belohnungssystem.md` speaks of "des Freizeit (o.ä.) Punktes", so free
 * time was always meant as a *kind* of block rather than one particular row.
 */
enum class ItemRole {
    FREE_TIME,
    BED_PREP,
    MORNING,
    /** The break inside a work block. */
    BREAK,
    WORK,
    MEAL,
    HOUSEKEEPING,
    SPORT,
    MINDFULNESS,
}

enum class ItemType {
    TODO,
    DEADLINE,
    RECURRING,
    SPEND,
}
