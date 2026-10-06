package com.example.eta.ui.attributes

import com.example.eta.domain.growth.GrowthFitIssue
import com.example.eta.domain.growth.growthTargetFit
import com.example.eta.domain.model.Item
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.recurrence.Rhythm

/**
 * The fit check, asked in the terms the form already holds.
 *
 * Its own function rather than a method on [RecurringAttributes], because it
 * needs the rest of the standing schedule and the form does not: the value is
 * what the user has typed, and what it would run into is a question for the
 * screen that knows the schedule.
 */
fun growthIssuesFor(
    attributes: RecurringAttributes,
    definitions: List<Item>,
    rhythm: Rhythm = Rhythm.Weekly,
    order: Int? = null,
    ignoreIds: Set<String> = emptySet(),
): List<GrowthFitIssue> {
    val growth = attributes.growth ?: return emptyList()
    if (attributes.weekdays.isEmpty()) return emptyList()
    return growthTargetFit(
        rules = rhythm.rulesFor(attributes.weekdays),
        earliestStart = attributes.startTime.minuteOfDay(),
        target = growth.target,
        travelBefore = attributes.travelBefore,
        returnAfter = attributes.returnAfter,
        breakAfter = attributes.breakAfter,
        dynamic = growth.dynamic,
        order = order,
        definitions = definitions,
        ignoreIds = ignoreIds,
    )
}
