package com.example.eta.domain.reevaluation

import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.Stage
import kotlin.time.Instant

/** What dropping an occurrence means for the item behind it. */
enum class DiscardConsequence {
    /** A one-shot ToDo goes back to the Sammelliste to be planned again. */
    RETURN_TO_COLLECTION,

    /** A missed recurring occurrence; ask whether it should be caught up. */
    OFFER_MAKE_UP,

    /** Deadlines, spends and imported events need no follow-up. */
    NONE,
}

fun consequenceOf(item: Item): DiscardConsequence = when (item.type) {
    // A break is not a task waiting for another day. Dropped, it is simply
    // not taken — a "Pause" in the Sammelliste would be a note about nothing.
    ItemType.TODO ->
        if (item.isOneOffBreak) DiscardConsequence.NONE else DiscardConsequence.RETURN_TO_COLLECTION
    ItemType.RECURRING -> DiscardConsequence.OFFER_MAKE_UP
    ItemType.DEADLINE, ItemType.SPEND -> DiscardConsequence.NONE
}

/**
 * A break slipped into one day by hand — from the planner's Pause revolver or
 * after a finished task — as opposed to the standing Pause the questionnaire
 * lays down, which is a recurring definition.
 *
 * It belongs to the day it stands on and to no list: taken off that day it is
 * gone, not handed back to be planned again.
 */
val Item.isOneOffBreak: Boolean
    get() = type == ItemType.TODO && role == ItemRole.BREAK

/**
 * Sends a dropped ToDo back to the Sammelliste.
 *
 * The original [Item.enteredCollectionAt] is kept on purpose: a ToDo that is
 * planned and dropped over and over is exactly what the Sperrliste is meant to
 * catch, so a round trip through a day plan must not reset its clock.
 */
fun Item.returnedToCollection(now: Instant): Item = copy(
    stage = Stage.COLLECTION,
    enteredCollectionAt = enteredCollectionAt ?: now,
    updatedAt = now,
)
