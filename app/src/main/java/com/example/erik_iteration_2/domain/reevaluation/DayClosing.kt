package com.example.erik_iteration_2.domain.reevaluation

import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.ItemType
import com.example.erik_iteration_2.domain.model.Stage
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
    ItemType.TODO -> DiscardConsequence.RETURN_TO_COLLECTION
    ItemType.RECURRING -> DiscardConsequence.OFFER_MAKE_UP
    ItemType.DEADLINE, ItemType.SPEND -> DiscardConsequence.NONE
}

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
