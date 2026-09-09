package com.example.erik_iteration_2.ui.attributes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Priority
import com.example.erik_iteration_2.domain.recurrence.weekdaysOf
import com.example.erik_iteration_2.domain.setup.WEEK
import com.example.erik_iteration_2.ui.components.ErikCheckbox
import com.example.erik_iteration_2.ui.components.ErikChoice
import com.example.erik_iteration_2.ui.components.ErikDurationPicker
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikStepper
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTimePicker
import com.example.erik_iteration_2.ui.components.ErikWeekdayPicker
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** What a freshly ticked margin is worth until it is changed. */
val DEFAULT_MARGIN: Duration = 15.minutes

/** Every card is an hour long until someone says otherwise. */
val DEFAULT_DURATION: Duration = 1.hours

/** The shortest a task, a journey or a break may be. */
val MIN_TASK_DURATION: Duration = 15.minutes
val MIN_MARGIN: Duration = 5.minutes

/**
 * No "ohne Kategorie" here, and that is deliberate.
 *
 * A ToDo without one is not `isConcretized` and cannot be planned, so offering it
 * in a form whose whole job is to finish a card would be offering a way to leave
 * it unfinished. The frame of the day the questionnaire lays down does carry a
 * null category — but nothing in these forms edits that.
 */
private val CATEGORY_OPTIONS = listOf(
    Category.FOKUS to "Fokus — voller Einsatz",
    Category.NEBENBEI to "Nebenbei — geht auch beiläufig",
    Category.ACHTSAM to "Achtsam — Zeit für dich",
)

private val PRIORITY_OPTIONS = listOf(
    Priority.URGENT_MUST to "Muss zeitnah geschehen",
    Priority.MUST to "Muss geschehen",
    Priority.URGENT_WANT to "Soll zeitnah geschehen",
    Priority.WANT to "Soll geschehen",
)

/**
 * Everything a ToDo needs to be plannable, plus what it brings with it.
 *
 * One value rather than a parameter list, because five screens now ask for these
 * and a positional list of eight arguments — three of them `Duration` — is a
 * place for two of them to be swapped silently.
 */
data class TodoAttributes(
    val category: Category = Category.FOKUS,
    val priority: Priority = Priority.MUST,
    /** Days from today. The stepper counts in days; the caller makes the date. */
    val inDays: Int = 7,
    val duration: Duration = DEFAULT_DURATION,
    val travelBefore: Duration? = null,
    val breakAfter: Duration? = null,
    val endSound: Boolean = true,
) {
    companion object {
        /**
         * The answers a card already carries, so editing starts where it stands.
         *
         * A card that has none — a bare Quick-Add — gets the defaults, which is
         * the same set the concretizing step always offered.
         */
        fun of(item: Item, today: LocalDate) = TodoAttributes(
            category = item.category ?: Category.FOKUS,
            priority = item.priority ?: Priority.MUST,
            inDays = item.targetDate?.let { today.daysUntil(it).coerceAtLeast(0) } ?: 7,
            duration = item.estimatedDuration ?: DEFAULT_DURATION,
            travelBefore = item.travelBefore,
            breakAfter = item.breakAfter,
            endSound = item.endSound,
        )
    }
}

/** The same for a standing task: weekdays, a time and a length instead. */
data class RecurringAttributes(
    val category: Category = Category.FOKUS,
    val weekdays: Set<DayOfWeek> = setOf(WEEK.first()),
    val startTime: LocalTime = LocalTime(18, 0),
    val duration: Duration = DEFAULT_DURATION,
    val travelBefore: Duration? = null,
    val breakAfter: Duration? = null,
    val endSound: Boolean = true,
) {
    companion object {
        fun of(item: Item) = RecurringAttributes(
            category = item.category ?: Category.FOKUS,
            weekdays = item.recurrenceRule?.weekdaysOf() ?: setOf(WEEK.first()),
            startTime = item.startTime ?: LocalTime(18, 0),
            duration = item.estimatedDuration ?: DEFAULT_DURATION,
            travelBefore = item.travelBefore,
            breakAfter = item.breakAfter,
            endSound = item.endSound,
        )
    }
}

/**
 * What a ToDo is asked, wherever it is asked.
 *
 * [showTargetDate] is the one thing that varies: a card dropped straight onto a
 * slot in the day planner has already answered when it happens, and asking again
 * would invite two different answers.
 */
@Composable
fun TodoAttributeFields(
    value: TodoAttributes,
    onChange: (TodoAttributes) -> Unit,
    today: LocalDate,
    showTargetDate: Boolean = true,
) {
    ErikField(label = "Kategorie") {
        ErikChoice(
            options = CATEGORY_OPTIONS,
            selected = value.category,
            onSelect = { onChange(value.copy(category = it)) },
        )
    }
    ErikField(label = "Priorität") {
        ErikChoice(
            options = PRIORITY_OPTIONS,
            selected = value.priority,
            onSelect = { onChange(value.copy(priority = it)) },
        )
    }
    if (showTargetDate) {
        ErikField(
            label = "Zieldatum",
            // The card unlocks a week early, which is worth saying out loud.
            hint = "Am ${today.plus(DatePeriod(days = value.inDays)).formatLong()}. " +
                "Planbar wird es eine Woche vorher.",
        ) {
            ErikStepper(
                value = when (value.inDays) {
                    0 -> "heute"
                    1 -> "morgen"
                    else -> "in ${value.inDays} Tagen"
                },
                valueWidth = 104.dp,
                onDecrement = { onChange(value.copy(inDays = (value.inDays - 1).coerceAtLeast(0))) },
                onIncrement = { onChange(value.copy(inDays = value.inDays + 1)) },
            )
        }
    }
    ErikField(label = "Geschätzte Dauer") {
        ErikDurationPicker(
            value = value.duration,
            onValueChange = { onChange(value.copy(duration = it)) },
            minimum = MIN_TASK_DURATION,
        )
    }

    ExtrasFields(
        endSound = value.endSound,
        onEndSound = { onChange(value.copy(endSound = it)) },
        travel = value.travelBefore,
        onTravel = { onChange(value.copy(travelBefore = it)) },
        breakAfter = value.breakAfter,
        onBreakAfter = { onChange(value.copy(breakAfter = it)) },
    )
}

/**
 * What a standing task is asked.
 *
 * No priority and no target date: a recurring definition has neither. What it
 * does need is exactly the three `expandRecurring` refuses to guess at — which
 * days, when, and how long — without which it lays down no occurrence at all.
 */
@Composable
fun RecurringAttributeFields(
    value: RecurringAttributes,
    onChange: (RecurringAttributes) -> Unit,
) {
    ErikField(label = "Kategorie") {
        ErikChoice(
            options = CATEGORY_OPTIONS,
            selected = value.category,
            onSelect = { onChange(value.copy(category = it)) },
        )
    }
    ErikField(
        label = "Wochentage",
        hint = if (value.weekdays.size == WEEK.size) {
            "Jeden Tag."
        } else {
            "Jeder Tag wird eine eigene Aufgabe — so lässt sich später einer davon " +
                "verschieben, ohne die anderen mitzunehmen."
        },
    ) {
        ErikWeekdayPicker(
            selected = value.weekdays,
            onToggle = { day ->
                val next = if (day in value.weekdays) {
                    value.weekdays - day
                } else {
                    value.weekdays + day
                }
                onChange(value.copy(weekdays = next))
            },
            days = WEEK,
        )
    }
    ErikField(label = "Beginn") {
        ErikTimePicker(
            value = value.startTime,
            onValueChange = { onChange(value.copy(startTime = it)) },
        )
    }
    ErikField(label = "Dauer") {
        ErikDurationPicker(
            value = value.duration,
            onValueChange = { onChange(value.copy(duration = it)) },
            minimum = MIN_TASK_DURATION,
        )
    }

    ExtrasFields(
        endSound = value.endSound,
        onEndSound = { onChange(value.copy(endSound = it)) },
        travel = value.travelBefore,
        onTravel = { onChange(value.copy(travelBefore = it)) },
        breakAfter = value.breakAfter,
        onBreakAfter = { onChange(value.copy(breakAfter = it)) },
    )
}

/**
 * The three switches both forms carry.
 *
 * The two margins are defaults for the blocks a definition will produce rather
 * than part of the task itself; the block carries them, and dragging the task
 * drags them with it. `task_end.mp3` is off for everything that predates the
 * switch and on for everything answered through one of these forms.
 */
@Composable
fun ExtrasFields(
    endSound: Boolean,
    onEndSound: (Boolean) -> Unit,
    travel: Duration?,
    onTravel: (Duration?) -> Unit,
    breakAfter: Duration?,
    onBreakAfter: (Duration?) -> Unit,
) {
    CheckRow(
        checked = endSound,
        onCheckedChange = onEndSound,
        label = "Ton am Ende",
        hint = "Meldet sich, wenn die geplante Zeit abgelaufen ist — nicht beim Abhaken.",
    )
    MarginRow(
        label = "Anfahrtszeit",
        hint = "Liegt vor der Aufgabe und gehört zu ihr — sie wird mitverschoben.",
        value = travel,
        onValueChange = onTravel,
    )
    MarginRow(
        label = "Pause danach",
        hint = "Liegt hinter der Aufgabe. Passt sie an keiner Stelle des Tages, wird " +
            "beim Einplanen gefragt.",
        value = breakAfter,
        onValueChange = onBreakAfter,
    )
}

/** One margin: off, or a length. Ticking it starts at a quarter of an hour. */
@Composable
private fun MarginRow(
    label: String,
    hint: String,
    value: Duration?,
    onValueChange: (Duration?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
        CheckRow(
            checked = value != null,
            onCheckedChange = { onValueChange(if (it) DEFAULT_MARGIN else null) },
            label = label,
            hint = hint,
        )
        if (value != null) {
            ErikDurationPicker(
                value = value,
                onValueChange = onValueChange,
                minimum = MIN_MARGIN,
            )
        }
    }
}

/**
 * A labelled checkbox with a line of explanation.
 *
 * Its own thing rather than an [ErikField], because what it labels is the box
 * itself rather than a control underneath one.
 */
@Composable
fun CheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    hint: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        ErikCheckbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(ErikTheme.spacing.md))
        Column {
            ErikText(text = label, style = ErikTheme.typography.bodyStrong)
            ErikText(
                text = hint,
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
        }
    }
}
