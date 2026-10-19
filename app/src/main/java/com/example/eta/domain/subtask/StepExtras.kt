package com.example.eta.domain.subtask

import com.example.eta.domain.growth.GrowthSetting
import com.example.eta.domain.growth.QuantitySetting
import com.example.eta.domain.growth.TimerSetting
import com.example.eta.domain.growth.advancedByCompletion
import com.example.eta.domain.growth.growthSetting
import com.example.eta.domain.growth.isFullyGrown
import com.example.eta.domain.growth.isGrowthTask
import com.example.eta.domain.growth.quantityLabel
import com.example.eta.domain.growth.quantitySetting
import com.example.eta.domain.growth.timerDuration
import com.example.eta.domain.growth.timerSetting
import com.example.eta.domain.growth.withBufferAdded
import com.example.eta.domain.growth.withGrowth
import com.example.eta.domain.growth.withQuantity
import com.example.eta.domain.growth.withTimer
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.SubtaskTimer
import com.example.eta.domain.model.normalizeName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** What a step's timer starts at when the box is first ticked. */
val DEFAULT_STEP_DURATION: Duration = 5.minutes

/**
 * What a routine's step can be beyond its name (step 49): a Timer-Task — the
 * time on its timer, a buffer, a pomodoro rhythm, a "Zielzeit" — and a
 * Mengen-Inkrement. The builder's counterpart of `ItemExtras`.
 *
 * [duration] is the time on the timer; with a "Zielzeit" it is where growing
 * has got to, and the setting's start is where it began.
 */
data class StepExtras(
    val timer: TimerSetting? = null,
    val duration: Duration? = null,
    val pomodoroWork: Duration? = null,
    val pomodoroPause: Duration? = null,
    val growth: GrowthSetting? = null,
    val quantity: QuantitySetting? = null,
) {
    val isEmpty: Boolean get() = timer == null && quantity == null

    companion object {
        fun of(step: Subtask): StepExtras {
            val proxy = step.asItem()
            return StepExtras(
                timer = proxy.timerSetting,
                duration = step.duration,
                pomodoroWork = step.pomodoroWork,
                pomodoroPause = step.pomodoroPause,
                growth = proxy.growthSetting,
                quantity = proxy.quantitySetting,
            )
        }
    }
}

/**
 * The step as an [Item] — a stand-in carrying only what the growth, timer and
 * quantity arithmetic reads, so a step grows and counts by exactly the rules a
 * task does rather than by a copy of them. Its length is the item's: the time
 * on the timer **plus** the buffer.
 */
fun Subtask.asItem(): Item = Item(
    id = id,
    type = ItemType.RECURRING,
    name = name,
    stage = Stage.DAY,
    estimatedDuration = duration?.let { it + (timerBuffer ?: Duration.ZERO) },
    pomodoroWork = pomodoroWork,
    pomodoroPause = pomodoroPause,
    growthStart = growthStart,
    growthTarget = growthTarget,
    growthIncrement = growthIncrement,
    growthEvery = growthEvery,
    growthProgress = growthProgress,
    timerTask = timerTask,
    timerBuffer = timerBuffer,
    quantity = quantity,
    quantityStart = quantityStart,
    quantityIncrement = quantityIncrement,
    quantityTarget = quantityTarget,
    quantityEvery = quantityEvery,
    quantityProgress = quantityProgress,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** The stand-in's answers written back onto the step. */
fun Subtask.withItemFields(item: Item): Subtask = copy(
    timerTask = item.timerTask,
    duration = if (item.timerTask) item.timerDuration else null,
    timerBuffer = item.timerBuffer,
    pomodoroWork = item.pomodoroWork,
    pomodoroPause = item.pomodoroPause,
    growthStart = item.growthStart,
    growthTarget = item.growthTarget,
    growthIncrement = item.growthIncrement,
    growthEvery = item.growthEvery,
    growthProgress = item.growthProgress,
    quantity = item.quantity,
    quantityStart = item.quantityStart,
    quantityIncrement = item.quantityIncrement,
    quantityTarget = item.quantityTarget,
    quantityEvery = item.quantityEvery,
    quantityProgress = item.quantityProgress,
)

/**
 * The builder's answers on the step — the order of the item's save paths: the
 * form's time, the timer, the "Zielzeit", the count, and the buffer on top.
 *
 * Without the timer there is no time, rhythm or "Zielzeit"; a step merely
 * edited keeps where growing and counting have got to, as a task does.
 */
fun Subtask.withStepExtras(extras: StepExtras): Subtask {
    val timer = extras.timer
    val base = asItem().copy(
        estimatedDuration = if (timer == null) null else extras.duration ?: duration ?: DEFAULT_STEP_DURATION,
        pomodoroWork = extras.pomodoroWork?.takeIf { timer != null && extras.pomodoroPause != null },
        pomodoroPause = extras.pomodoroPause?.takeIf { timer != null && extras.pomodoroWork != null },
    )
    val item = base
        .withTimer(timer)
        .withGrowth(extras.growth?.takeIf { timer != null }?.copy(dynamic = false))
        .withQuantity(extras.quantity)
        .let { if (timer != null) it.withBufferAdded() else it }
    return withItemFields(item)
}

val Subtask.isTimerStep: Boolean get() = timerTask && duration != null

val Subtask.isGrowingStep: Boolean get() = asItem().isGrowthTask

val Subtask.isFullyGrownStep: Boolean get() = asItem().isFullyGrown

/** "Anzahl 12", or "Anzahl 12 / 20" — as a task says it. */
fun Subtask.quantityLabel(): String? = asItem().quantityLabel()

/**
 * One more confirmed completion: growth and count advanced by their own update
 * condition. [timeCounts] is false for a sitting not worked in full — the count
 * still moves, the time does not, as for a task.
 */
fun Subtask.advanced(timeCounts: Boolean): Subtask = withItemFields(asItem().advancedByCompletion(timeCounts))

/**
 * What makes two step rows the same step: the same list is saved onto every
 * weekday of a standing task, so "Duschen" in the morning routine is seven
 * rows, and they grow and count together — by the task's name and the step's.
 */
data class StepFamily(val taskName: String, val stepName: String)

fun Subtask.familyIn(parent: Item): StepFamily = StepFamily(parent.normalizedName, normalizeName(name))

/** What growing and counting move, carried from one row of a family to the others. */
fun Subtask.withProgressOf(source: Subtask): Subtask = copy(
    duration = source.duration,
    growthProgress = source.growthProgress,
    quantity = source.quantity,
    quantityProgress = source.quantityProgress,
)

/**
 * Whether a step's sitting counts towards its "Zielzeit": its timer counted
 * the whole goal. A step ticked without its timer ever started is taken at its
 * word, as "as planned" is for a task.
 */
fun SubtaskTimer?.stepWorkedInFull(): Boolean = this == null || worked >= goal

/**
 * The step's timer as a block, so the timer arithmetic of `TaskTimer.kt` —
 * elapsed, remaining, the pomodoro phase, the signals — serves it unchanged.
 * It keeps the routine's own hours: its frame is the routine's.
 */
fun SubtaskTimer?.asBlock(parent: PlannedBlock, step: Subtask): PlannedBlock = parent.copy(
    id = "${parent.id}:${step.id}",
    timerGoal = this?.goal,
    timerWorked = this?.worked,
    timerRunningSince = this?.runningSince,
    pomodoroWork = step.pomodoroWork,
    pomodoroPause = step.pomodoroPause,
    actualDuration = null,
    timerExtension = null,
)

/**
 * What the timer steps of a routine hold together, buffers included — set
 * against the routine's own length by the builder's warning.
 */
fun List<Subtask>.timedLength(): Duration =
    filter { it.isTimerStep }.fold(Duration.ZERO) { sum, step ->
        sum + (step.duration ?: Duration.ZERO) + (step.timerBuffer ?: Duration.ZERO)
    }

/** The same for the builder's drafts, with the "Zielzeit" each could grow to. */
fun List<StepExtras>.timedLength(grown: Boolean): Duration =
    filter { it.timer != null }.fold(Duration.ZERO) { sum, extras ->
        val time = if (grown) extras.growth?.target ?: extras.duration else extras.duration
        sum + (time ?: DEFAULT_STEP_DURATION) + (extras.timer?.buffer ?: Duration.ZERO)
    }
