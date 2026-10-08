package com.example.eta.domain.subtask

import com.example.eta.domain.model.Subtask
import com.example.eta.domain.planning.MIN_BLOCK_DURATION
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The step the remainder of a group is rounded to. */
private val REMAINDER_STEP: Duration = 5.minutes

/**
 * A subtask as a form holds it: no id until it has been saved.
 *
 * The forms work on drafts rather than on rows, because a card can be given
 * subtasks before it exists — the concretizing step and every "neu anlegen"
 * dialog fill the list in and the item id only arrives on Sichern.
 */
data class SubtaskDraft(
    val id: String? = null,
    val name: String,
    val note: String? = null,
)

/** The rows of a saved group, as the builder wants them. */
fun List<Subtask>.drafts(): List<SubtaskDraft> =
    sortedBy { it.position }.map { SubtaskDraft(id = it.id, name = it.name, note = it.note) }

/** One row dragged to another place in the list. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val rest = toMutableList()
    rest.add(to, rest.removeAt(from))
    return rest
}

/** How far through a group one day has got. */
data class SubtaskProgress(val done: Int, val total: Int) {
    val isEmpty: Boolean get() = total == 0
    val open: Int get() = total - done
    val allDone: Boolean get() = total > 0 && done == total
}

/** Which of a group's steps are still open on the day [checked] was read for. */
fun List<Subtask>.stillOpen(checked: Set<String>): List<Subtask> =
    sortedBy { it.position }.filterNot { it.id in checked }

fun List<Subtask>.progress(checked: Set<String>): SubtaskProgress =
    SubtaskProgress(done = count { it.id in checked }, total = size)

/**
 * One task about to become a subtask: what it is called, and what it cost.
 *
 * The margins travel along only so [mergedGroup] can decide which of them the
 * group inherits. Everything else a task carries — category, priority, reminder,
 * pomodoro rhythm, growth, deadline — is *not* here, because it does not survive
 * the merge, and the confirmation names all of it before it happens.
 */
data class MergePart(
    val name: String,
    val note: String? = null,
    val duration: Duration,
    val travelBefore: Duration? = null,
    val returnAfter: Duration? = null,
    val breakAfter: Duration? = null,
)

/** What a group looks like once its parts have been folded into it. */
data class MergedGroup(
    val subtasks: List<SubtaskDraft>,
    val duration: Duration,
    val travelBefore: Duration?,
    val returnAfter: Duration?,
    val breakAfter: Duration?,
)

/**
 * The length a group gets from the tasks folded into it: the sum of their **pure**
 * durations, with no margin in it.
 *
 * Confirmed with the user, and the reason matters: margins pay no points and task
 * time does, so absorbing a commute into the duration would quietly turn 30
 * minutes of driving into half a Fokus point and make grouping the most lucrative
 * gesture in the app. This way grouping is worth exactly what the parts were
 * worth. The same sum is what a group's length grows by when a further task joins
 * it later.
 */
fun mergedDuration(durations: List<Duration>): Duration =
    durations.fold(Duration.ZERO) { sum, part -> sum + part }
        .coerceAtLeast(MIN_BLOCK_DURATION)

/**
 * The whole arithmetic of a merge, in the order the user put the parts in.
 *
 * The group **inherits the outer margins**: the first part's journey there, the
 * last part's journey back and break. Only the margins *between* the parts fall
 * away — those were the gaps between two errands that are now one errand. The
 * drive to the first of them is still a drive, and dropping it would plan the
 * group wrong.
 *
 * Derived once, at the merge. They belong to the group afterwards and are edited
 * in Extras like any other task's: re-deriving them when the list is reordered
 * would let a drag in the builder silently change when the block starts.
 */
fun mergedGroup(parts: List<MergePart>): MergedGroup = MergedGroup(
    subtasks = parts.map { SubtaskDraft(name = it.name, note = it.note) },
    duration = mergedDuration(parts.map { it.duration }),
    travelBefore = parts.firstOrNull()?.travelBefore,
    returnAfter = parts.lastOrNull()?.returnAfter,
    breakAfter = parts.lastOrNull()?.breakAfter,
)

/**
 * How long the group is that the evening carries the unfinished steps over in.
 *
 * Proportional to what is left — three of four steps open is three quarters of
 * the length — rounded to five minutes, and never shorter than the shortest block
 * the planner can hold or longer than the group it came from. A share of the
 * length is a guess, but it is the only one available: a subtask has no duration
 * of its own, and asking the user for a length at the end of a long day is asking
 * the wrong question at the worst moment. The card is an ordinary ToDo afterwards
 * and can be corrected in one tap.
 */
fun remainderDuration(groupDuration: Duration, open: Int, total: Int): Duration {
    if (total <= 0 || open <= 0) return MIN_BLOCK_DURATION
    if (open >= total) return groupDuration
    val share = groupDuration.inWholeSeconds.toDouble() * open / total
    val steps = (share / REMAINDER_STEP.inWholeSeconds).roundToInt()
    val floor = minOf(MIN_BLOCK_DURATION, groupDuration)
    return (REMAINDER_STEP * steps).coerceIn(floor, groupDuration)
}

/**
 * A plain task folded into a group that already has steps.
 *
 * It joins at the **end**: the group is a sequence the user put in an order, and
 * dropping something into the middle of it would be guessing where. Its own
 * subtasks — a group folded into a group — follow in their own order, which is
 * the only reading under which "die subtasks werden dann in die andere
 * transferiert" keeps meaning something.
 */
fun List<Subtask>.foldedWith(added: List<SubtaskDraft>): List<SubtaskDraft> =
    drafts() + added.map { SubtaskDraft(name = it.name, note = it.note) }

/** The same, for a task that is not a group: it becomes one step. */
fun List<Subtask>.foldedWith(name: String, note: String? = null): List<SubtaskDraft> =
    foldedWith(listOf(SubtaskDraft(name = name, note = note)))

/**
 * The two parts of a merge, in the order the finished list puts them.
 *
 * The margins the group inherits depend on which task ends up first and which
 * last, and the builder may have been reordered before Sichern — so the parts are
 * matched back to the list by name, which is the only handle they have: a draft
 * that has never been saved carries no id. Anything the list gained in the builder
 * is not a task and has no margins to contribute, so it cannot change the answer.
 */
fun orderedParts(parts: List<MergePart>, list: List<SubtaskDraft>): List<MergePart> {
    val position = list.mapIndexed { index, draft -> draft.name to index }.toMap()
    return parts.sortedBy { position[it.name] ?: Int.MAX_VALUE }
}

/**
 * Which existing row each draft lands on — its id, or null for a new row.
 *
 * A draft that carries the id of one of [existing] keeps that row, and with it
 * every tick standing against it. A draft whose id belongs to **another** card
 * does not: a standing task is one definition per weekday, the form holds the
 * steps of one of them, and the same list is then saved onto each. Writing those
 * rows under their foreign ids would move them from one weekday to the next, and
 * only the last one saved would still have any. Such a draft takes the row of the
 * same name here, if there is one nobody else claimed, and is new otherwise.
 */
fun List<SubtaskDraft>.matchedTo(existing: List<Subtask>): List<String?> {
    val own = existing.map { it.id }.toSet()
    val claimed = mapNotNull { it.id }.filter { it in own }.toMutableSet()
    return map { draft ->
        draft.id?.takeIf { it in own }
            ?: existing.firstOrNull { it.id !in claimed && it.name == draft.name.trim() }
                ?.id
                ?.also { claimed += it }
    }
}

/**
 * Where a routine stands: the one step that is due, and the one before it.
 *
 * A routine is worked through in the order of its list, so what is due is simply
 * the first step not ticked yet — ticking out of order cannot happen, there being
 * only the one checkbox. [previous] is the last one done, which is what the box
 * names with its time, and what a mistaken tick is taken back from.
 */
data class RoutineProgress(
    val current: Subtask?,
    val previous: Subtask?,
    val done: Int,
    val total: Int,
) {
    val isFinished: Boolean get() = total > 0 && current == null
}

fun List<Subtask>.routineProgress(checked: Set<String>): RoutineProgress {
    val ordered = sortedBy { it.position }
    return RoutineProgress(
        current = ordered.firstOrNull { it.id !in checked },
        previous = ordered.lastOrNull { it.id in checked },
        done = ordered.count { it.id in checked },
        total = ordered.size,
    )
}
