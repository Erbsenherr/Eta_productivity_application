package com.example.eta.domain.planning

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** What the Pausenchecker asks for until the settings say otherwise. */
val DEFAULT_BREAK_PER_TWO_HOURS: Duration = 15.minutes
val DEFAULT_BREAK_MAX_STRETCH: Duration = 3.hours

/** The stretch of work the first rule is counted in: "für jede 2 Stunden". */
private const val BREAK_UNIT_MINUTES = 120

/** The two warnings the planner can raise about breaks, each waved away by itself. */
enum class PlannerWarning { BREAK_TOTAL, BREAK_STRETCH }

/**
 * What a block is to the Pausenchecker.
 *
 * [REST] is time off, [TASK] is what one needs time off from, and [NEUTRAL] is
 * neither — the frame of the day, which is not work and is not a break from it.
 */
enum class BreakKind { REST, TASK, NEUTRAL }

/**
 * Which of the three a block is — the user's own list, where it was theirs to
 * make: breaks, free time, meals and mindfulness are rest, and so is a **Custom
 * Spend**, which is leisure paid for in points. Social, the neutral points
 * entry, is rest for the same reason; a Custom Earn is work.
 *
 * The role is asked before the category, because it is the firmer statement:
 * cooking carries "Nebenbei" so that it pays, and is still the meal.
 */
fun BlockWithItem.breakKind(): BreakKind = when {
    item.role in REST_ROLES -> BreakKind.REST
    item.type == ItemType.SPEND ->
        if ((item.pointsPerHour ?: 0.0) > 0.0) BreakKind.TASK else BreakKind.REST
    item.category == Category.ACHTSAM -> BreakKind.REST
    item.role == ItemRole.BED_PREP || item.role == ItemRole.MORNING -> BreakKind.NEUTRAL
    item.category != null || item.role != null -> BreakKind.TASK
    block.origin == BlockOrigin.CALENDAR_IMPORT -> BreakKind.TASK
    else -> BreakKind.NEUTRAL
}

private val REST_ROLES = setOf(ItemRole.BREAK, ItemRole.FREE_TIME, ItemRole.MEAL, ItemRole.MINDFULNESS)

/** A run of tasks with no rest in it that is longer than the settings allow. */
data class LongStretch(
    /** From the start of its first task to the end of its last, minutes of the day. */
    val fromMinute: Int,
    val toMinute: Int,
    /** The task time in it — what is measured, since a gap between tasks is not work. */
    val taskMinutes: Int,
)

data class BreakFindings(
    val taskMinutes: Int,
    val restMinutes: Int,
    /** What the tasks of the day ask for: so much for every full two hours. */
    val neededMinutes: Int,
    val longStretches: List<LongStretch>,
) {
    val tooFew: Boolean get() = restMinutes < neededMinutes
}

/**
 * The Pausenchecker: a day read twice.
 *
 * **In total** — for every *full* two hours of tasks, [perTwoHours] of rest.
 * Full, so that a single hour of work does not ask for seven and a half
 * minutes: the rule is spoken in two-hour steps and counted in them.
 *
 * **Stretch by stretch** — between two rests, before the first and after the
 * last, no more than [maxStretch] of tasks. Measured in **task time**, not on
 * the clock: an afternoon nothing is planned into is not three hours of work,
 * and reading it as such would warn about every free evening. A neutral block
 * neither adds to a stretch nor ends one.
 *
 * Rest is every [BreakKind.REST] block and every "Pause danach" a task brings.
 * Pomodoro pauses are not counted, by the user's rule: these breaks come on top
 * of them. Called-off blocks have left the day and are not read at all; done
 * ones are — the day is the same day once its morning is ticked off.
 */
fun checkBreaks(
    blocks: List<BlockWithItem>,
    perTwoHours: Duration,
    maxStretch: Duration,
): BreakFindings {
    val day = blocks.filter { it.occupiesTime() }.sortedBy { it.block.startMinute() }
    val limit = maxStretch.inWholeMinutes.toInt()

    var taskMinutes = 0
    var restMinutes = 0
    val stretches = mutableListOf<LongStretch>()

    var from = -1
    var to = 0
    var run = 0
    fun endStretch() {
        if (from >= 0 && run > limit) stretches += LongStretch(from, to, run)
        from = -1
        run = 0
    }

    day.forEach { entry ->
        val length = entry.block.effectiveDuration.inWholeMinutes.toInt()
        when (entry.breakKind()) {
            BreakKind.REST -> {
                restMinutes += length
                endStretch()
            }

            BreakKind.TASK -> {
                taskMinutes += length
                if (from < 0) from = entry.block.startMinute()
                to = maxOf(to, entry.block.endMinute())
                run += length
            }

            BreakKind.NEUTRAL -> Unit
        }
        // The break a block brings follows it, whatever the block itself is.
        entry.block.breakAfter?.let { pause ->
            restMinutes += pause.inWholeMinutes.toInt()
            endStretch()
        }
    }
    endStretch()

    return BreakFindings(
        taskMinutes = taskMinutes,
        restMinutes = restMinutes,
        neededMinutes = (taskMinutes / BREAK_UNIT_MINUTES) * perTwoHours.inWholeMinutes.toInt(),
        longStretches = stretches,
    )
}
