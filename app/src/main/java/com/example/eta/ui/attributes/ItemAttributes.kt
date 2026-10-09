package com.example.eta.ui.attributes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eta.domain.growth.DEFAULT_GROWTH_INCREMENT
import com.example.eta.domain.growth.DEFAULT_GROWTH_START
import com.example.eta.domain.growth.DEFAULT_EVERY
import com.example.eta.domain.growth.DEFAULT_GROWTH_TARGET
import com.example.eta.domain.growth.MAX_EVERY
import com.example.eta.domain.growth.MIN_QUANTITY
import com.example.eta.domain.growth.QuantitySetting
import com.example.eta.domain.growth.GrowthFitIssue
import com.example.eta.domain.growth.GrowthFitProblem
import com.example.eta.domain.growth.GrowthSetting
import com.example.eta.domain.growth.MIN_GROWTH_DURATION
import com.example.eta.domain.growth.MIN_GROWTH_INCREMENT
import com.example.eta.domain.growth.growthSetting
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemExtras
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.planning.DEFAULT_POMODORO_PAUSE
import com.example.eta.domain.planning.DEFAULT_POMODORO_WORK
import com.example.eta.domain.planning.MIN_POMODORO_PHASE
import com.example.eta.domain.recurrence.RecurringOverlap
import com.example.eta.domain.recurrence.RecurringSlot
import com.example.eta.domain.recurrence.Rhythm
import com.example.eta.domain.recurrence.weekdaysOf
import com.example.eta.domain.reminder.DEFAULT_REMINDER_LEAD_HOURS
import com.example.eta.domain.reminder.MAX_REMINDER_LEAD_HOURS
import com.example.eta.domain.reminder.MIN_REMINDER_LEAD_HOURS
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.domain.subtask.drafts
import com.example.eta.ui.subtasks.FoldCandidate
import com.example.eta.ui.subtasks.addedDuration
import com.example.eta.ui.subtasks.SubtaskBuilderDialog
import com.example.eta.ui.subtasks.SubtaskSetting
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaCheckbox
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaCountPicker
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.components.EtaExpander
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaStepper
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.components.LocalFeatures
import com.example.eta.ui.components.EtaDatePickerDialog
import com.example.eta.ui.format.formatWithYear
import com.example.eta.domain.recurrence.rulesWithTimes
import androidx.compose.runtime.key
import com.example.eta.data.repository.RecurringEdit
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.components.EtaTimePicker
import com.example.eta.ui.components.EtaWeekdayPicker
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatWeekdays
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

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
    /**
     * "Freigeschaltet ab": the first day the card may be planned for. Null is
     * "Heute" — free at once — and is stored as no date at all, so finishing a
     * note that has waited three weeks does not move its one-month clock.
     */
    val unlockFrom: LocalDate? = null,
    val duration: Duration = DEFAULT_DURATION,
    val travelBefore: Duration? = null,
    val returnAfter: Duration? = null,
    val breakAfter: Duration? = null,
    val endSound: Boolean = true,
    /** The newer switches of the "Extras" box: pomodoro and the reminder. */
    val extras: ItemExtras = ItemExtras(),
    /**
     * The steps inside this card, in order.
     *
     * Carried with the answers rather than beside them, so every screen that
     * already saves a card saves its group too — six save paths, none of which
     * has to remember a seventh argument.
     */
    val subtasks: List<SubtaskDraft> = emptyList(),
    /**
     * Tasks the Subtask-Builder swallowed, to be retired when this card is saved.
     *
     * Carried rather than acted on, so cancelling the form leaves them standing.
     */
    val foldedItemIds: List<String> = emptyList(),
) {
    companion object {
        /**
         * The answers a card already carries, so editing starts where it stands.
         *
         * A card that has none — a bare Quick-Add — gets the defaults, which is
         * the same set the concretizing step always offered.
         */
        fun of(item: Item, today: LocalDate, subtasks: List<Subtask> = emptyList()) = TodoAttributes(
            category = item.category ?: Category.FOKUS,
            priority = item.priority ?: Priority.MUST,
            // Kept even when it has passed: the clock to the Sperrliste counts
            // from it, and an edit that dropped it would wind that clock back.
            unlockFrom = item.targetDate,
            duration = item.estimatedDuration ?: DEFAULT_DURATION,
            travelBefore = item.travelBefore,
            returnAfter = item.returnAfter,
            breakAfter = item.breakAfter,
            endSound = item.endSound,
            extras = ItemExtras.of(item),
            subtasks = subtasks.drafts(),
        )
    }
}

/**
 * The same for a standing task: weekdays, a time and a length instead.
 *
 * [category] may be null here, unlike a ToDo's: the frame of the day — Pause,
 * Freizeit, Morgenzeit — is a standing task that deliberately pays nothing, and
 * editing its time must not be a way of accidentally making it pay.
 *
 * [growth] is the Growth-Task sub-feature. When it is set, [duration] stops
 * being an answer of its own: it is where the length has got to, and growing
 * owns it — see `GrowthTask.kt`.
 */
data class RecurringAttributes(
    val category: Category? = Category.FOKUS,
    val weekdays: Set<DayOfWeek> = setOf(WEEK.first()),
    val startTime: LocalTime = LocalTime(18, 0),
    val duration: Duration = DEFAULT_DURATION,
    val travelBefore: Duration? = null,
    val returnAfter: Duration? = null,
    val breakAfter: Duration? = null,
    val endSound: Boolean = true,
    val extras: ItemExtras = ItemExtras(),
    val growth: GrowthSetting? = null,
    /**
     * "Abweichende Uhrzeiten": an hour of its own per weekday, or null while the
     * box is unticked and every weekday starts at [startTime]. A weekday missing
     * from the map — one ticked after the box was — starts at [startTime] too.
     */
    val startTimes: Map<DayOfWeek, LocalTime>? = null,
    /** The steps inside this task — see [TodoAttributes.subtasks]. */
    val subtasks: List<SubtaskDraft> = emptyList(),
    /** Tasks swallowed by the builder — see [TodoAttributes.foldedItemIds]. */
    val foldedItemIds: List<String> = emptyList(),
) {
    companion object {
        /**
         * A finished definition keeps whatever category it has, none included; a
         * bare note has not been asked yet and starts on the usual default.
         */
        fun of(item: Item, subtasks: List<Subtask> = emptyList()) = RecurringAttributes(
            category = if (item.isConcretized) item.category else item.category ?: Category.FOKUS,
            weekdays = item.recurrenceRule?.weekdaysOf() ?: setOf(WEEK.first()),
            startTime = item.startTime ?: LocalTime(18, 0),
            duration = item.estimatedDuration ?: DEFAULT_DURATION,
            travelBefore = item.travelBefore,
            returnAfter = item.returnAfter,
            breakAfter = item.breakAfter,
            endSound = item.endSound,
            extras = ItemExtras.of(item),
            growth = item.growthSetting,
            subtasks = subtasks.drafts(),
        )

        /**
         * What the Growth-Tasks tab opens with: the ordinary defaults, and the
         * box already ticked, since that is the button that was pressed.
         */
        fun newGrowth() = RecurringAttributes(
            duration = DEFAULT_GROWTH_START,
            growth = GrowthSetting.Default,
        )
    }

    /** The hour [day] starts at. */
    fun timeOn(day: DayOfWeek): LocalTime = ownStartTimes[day] ?: startTime

    /**
     * The hours that are really asked for: none while the box is unticked, and
     * none for a single weekday, which has only the one hour to differ from.
     */
    val ownStartTimes: Map<DayOfWeek, LocalTime>
        get() = startTimes?.takeIf { weekdays.size > 1 }?.filterKeys { it in weekdays }.orEmpty()

    /** Whether the weekdays start at more than one hour. */
    val hasOwnTimes: Boolean get() = weekdays.map(::timeOn).distinct().size > 1

    /** The slots these answers would lay down, for the overlap check. */
    fun slots(rhythm: Rhythm = Rhythm.Weekly): List<RecurringSlot> =
        rulesWithTimes(rhythm, weekdays, startTime, ownStartTimes).map { (rule, start) ->
            RecurringSlot(rule, start, duration, travelBefore, returnAfter, breakAfter)
        }
}

/**
 * The category question on its own.
 *
 * For the one screen that asks it without asking anything else: finishing a card
 * early, where a Quick-Add note that was never filled in still has to say what it
 * is worth. Shared rather than hand-rolled, so there is one list of the three
 * categories and their explanations.
 */
@Composable
fun CategoryField(
    value: Category,
    onChange: (Category) -> Unit,
    label: String = "Kategorie",
) {
    EtaField(label = label) {
        EtaChoice(options = CATEGORY_OPTIONS, selected = value, onSelect = onChange)
    }
}

/**
 * The priority question on its own, for the same reason [CategoryField] exists:
 * the Gruppieren dialog asks what a new group is worth without asking a ToDo
 * everything else.
 */
@Composable
fun PriorityField(
    value: Priority,
    onChange: (Priority) -> Unit,
    label: String = "Priorität",
) {
    EtaField(label = label) {
        EtaChoice(options = PRIORITY_OPTIONS, selected = value, onSelect = onChange)
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
    /**
     * The card's own name, which the Subtask-Builder edits along with the list —
     * a group is a task, and renaming the group renames the task. Null is "no
     * subtasks on offer here"; the name field itself belongs to the screen.
     */
    groupName: String? = null,
    onGroupName: ((String) -> Unit)? = null,
    /** Tasks that can be folded into this one as steps. */
    foldCandidates: List<FoldCandidate> = emptyList(),
) {
    EtaField(label = "Kategorie") {
        EtaChoice(
            options = CATEGORY_OPTIONS,
            selected = value.category,
            onSelect = { onChange(value.copy(category = it)) },
        )
    }
    EtaField(label = "Priorität") {
        EtaChoice(
            options = PRIORITY_OPTIONS,
            selected = value.priority,
            onSelect = { onChange(value.copy(priority = it)) },
        )
    }
    if (showTargetDate) {
        var picking by remember { mutableStateOf(false) }
        val unlock = value.unlockFrom
        val later = unlock != null && unlock > today
        EtaField(
            label = "Freigeschaltet ab",
            hint = if (later) {
                "Vorher lässt sich die Aufgabe nicht einplanen. Die Monatsfrist bis zur " +
                    "Sperrliste läuft erst ab diesem Tag."
            } else {
                "Sofort planbar. Antippen, um einen späteren Tag zu wählen."
            },
        ) {
            EtaButton(
                text = if (unlock == null || unlock == today) "Heute" else unlock.formatWithYear(),
                style = EtaButtonStyle.Secondary,
                onClick = { picking = true },
            )
        }
        if (picking) {
            EtaDatePickerDialog(
                title = "Freigeschaltet ab",
                value = unlock ?: today,
                today = today,
                onDismiss = { picking = false },
                onConfirm = { day ->
                    // Today is "at once", and at once is no date.
                    onChange(value.copy(unlockFrom = day.takeIf { it > today }))
                    picking = false
                },
            )
        }
    }
    EtaField(label = "Geschätzte Dauer") {
        EtaDurationPicker(
            value = value.duration,
            onValueChange = { onChange(value.copy(duration = it)) },
            minimum = MIN_TASK_DURATION,
        )
    }

    ExtrasBox(
        endSound = value.endSound,
        onEndSound = { onChange(value.copy(endSound = it)) },
        travel = value.travelBefore,
        onTravel = { onChange(value.copy(travelBefore = it)) },
        returnAfter = value.returnAfter,
        onReturnAfter = { onChange(value.copy(returnAfter = it)) },
        breakAfter = value.breakAfter,
        onBreakAfter = { onChange(value.copy(breakAfter = it)) },
        extras = value.extras,
        onExtras = { onChange(value.copy(extras = it)) },
        today = today,
        subtasks = groupName?.let { SubtaskSetting(it, value.subtasks) },
        onSubtasks = onGroupName?.let { rename ->
            { setting: SubtaskSetting ->
                rename(setting.groupName)
                onChange(
                    value.copy(
                        subtasks = setting.subtasks,
                        // A folded task's minutes move into the group at once, so
                        // the length on screen is the one that will be saved.
                        duration = value.duration + setting.folded.addedDuration(),
                        foldedItemIds = value.foldedItemIds + setting.folded.map { it.itemId },
                    ),
                )
            }
        },
        foldCandidates = foldCandidates,
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
    /**
     * Offer "no category". Only where an existing standing task is edited: a new
     * note being finished should end up paying for itself, while the frame of the
     * day already carries none and has to be able to keep it that way.
     */
    allowNoCategory: Boolean = false,
    /** Other standing tasks the answers collide with, shown under the fields. */
    overlaps: List<RecurringOverlap> = emptyList(),
    /**
     * Where these answers would next sit clear of everything else, for the
     * "Nächster freier Slot" button under the overlap warning. The caller knows
     * the week they are measured against; null offers no button, and a null
     * *answer* says the rest of the day has no such stretch.
     */
    findNextFree: ((RecurringAttributes) -> LocalTime?)? = null,
    /**
     * Whether the Growth-Task box is on the table at all.
     *
     * It is a property of a *standing* task, so the three forms that fill one in
     * offer it: the Growth-Tasks tab, the Listen tab's editor and the evening's
     * concretizing step.
     */
    allowGrowth: Boolean = false,
    /** Weekdays on which the target length will not fit, from `growthTargetFit`. */
    growthIssues: List<GrowthFitIssue> = emptyList(),
    /** The task's own name, for the Subtask-Builder — see [TodoAttributeFields]. */
    groupName: String? = null,
    onGroupName: ((String) -> Unit)? = null,
    /** Tasks that can be folded into this one as steps. */
    foldCandidates: List<FoldCandidate> = emptyList(),
) {
    // Out of sight with the Growth-Tasks feature — except on a task that already
    // grows, which has to stay reachable so the growing can be switched off.
    val offerGrowth = allowGrowth && (LocalFeatures.current.growthTasks || value.growth != null)

    EtaField(
        label = "Kategorie",
        hint = if (value.category == null && LocalPointsVisible.current) {
            "Zählt nicht für Punkte."
        } else {
            null
        },
    ) {
        val options: List<Pair<Category?, String>> =
            if (allowNoCategory || value.category == null) {
                CATEGORY_OPTIONS + (null to "Keine — gehört zum Rahmen des Tages")
            } else {
                CATEGORY_OPTIONS
            }
        EtaChoice(
            options = options,
            selected = value.category,
            onSelect = { onChange(value.copy(category = it)) },
        )
    }
    EtaField(
        label = "Wochentage",
        hint = if (value.weekdays.size == WEEK.size) {
            "Jeden Tag."
        } else {
            "Jeder Tag wird eine eigene Aufgabe — so lässt sich später einer davon " +
                "verschieben, ohne die anderen mitzunehmen."
        },
    ) {
        EtaWeekdayPicker(
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
    // Only where there is more than one day to differ: with a single weekday
    // the box would switch on a second picker for the same hour.
    if (value.weekdays.size > 1) {
        CheckRow(
            checked = value.startTimes != null,
            onCheckedChange = { on ->
                onChange(
                    value.copy(
                        // Every day opens on the hour the form already had, so
                        // ticking the box changes nothing until an hour is moved.
                        startTimes = if (on) value.weekdays.associateWith { value.startTime } else null,
                    ),
                )
            },
            label = "Abweichende Uhrzeiten",
            hint = "Wiederkehrende Tasks finden innerhalb einer Woche zu " +
                "unterschiedlichen Zeiten statt.",
        )
    }
    val beginLabel = if (value.growth?.dynamic == true) "Frühster Beginn" else "Beginn"
    val ownTimes = value.startTimes?.takeIf { value.weekdays.size > 1 }
    if (ownTimes == null) {
        EtaField(
            label = beginLabel,
            hint = if (value.growth?.dynamic == true) {
                "Bei dynamischer Zeitsetzung die früheste Uhrzeit — ist sie belegt, " +
                    "beginnt die Aufgabe direkt danach."
            } else {
                null
            },
        ) {
            EtaTimePicker(
                value = value.startTime,
                onValueChange = { onChange(value.copy(startTime = it)) },
            )
        }
    } else {
        WEEK.filter { it in value.weekdays }.forEach { day ->
            key(day) {
                EtaField(label = "$beginLabel am ${day.formatLong()}") {
                    EtaTimePicker(
                        value = value.timeOn(day),
                        onValueChange = { onChange(value.copy(startTimes = ownTimes + (day to it))) },
                    )
                }
            }
        }
        if (value.hasOwnTimes) {
            EtaText(
                text = "Tage mit eigener Uhrzeit stehen danach als eigene Einträge in der " +
                    "Liste und werden einzeln bearbeitet.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
    }
    if (value.growth == null) {
        EtaField(label = "Dauer") {
            EtaDurationPicker(
                value = value.duration,
                onValueChange = { onChange(value.copy(duration = it)) },
                minimum = MIN_TASK_DURATION,
            )
        }
    } else {
        // Growing owns the length, so there is nothing to set here — only
        // something to say. Two answers for one number would be one too many.
        EtaField(
            label = "Dauer",
            hint = if (value.growth.every > 1) {
                "Wächst alle ${value.growth.every} Abschlüsse."
            } else {
                "Wächst mit jedem Abschluss."
            },
        ) {
            EtaText(
                text = "Aktuell ${value.duration.formatShort()} · " +
                    "Ziel ${value.growth.target.formatShort()}",
                style = EtaTheme.typography.bodyStrong,
                color = EtaTheme.colors.accent,
            )
        }
    }

    RepeatUntilRow(extras = value.extras, onExtras = { onChange(value.copy(extras = it)) })

    // Right under the three answers that cause it, and above the margins, which
    // can cause it too but are read after.
    OverlapWarning(
        overlaps = overlaps,
        // One free hour for every weekday is not an answer to a form that has
        // just asked for several different ones.
        findNextFree = findNextFree?.takeIf { !value.hasOwnTimes }?.let { find -> { find(value) } },
        onMoveTo = { onChange(value.copy(startTime = it)) },
    )

    ExtrasBox(
        endSound = value.endSound,
        onEndSound = { onChange(value.copy(endSound = it)) },
        travel = value.travelBefore,
        onTravel = { onChange(value.copy(travelBefore = it)) },
        returnAfter = value.returnAfter,
        onReturnAfter = { onChange(value.copy(returnAfter = it)) },
        breakAfter = value.breakAfter,
        onBreakAfter = { onChange(value.copy(breakAfter = it)) },
        extras = value.extras,
        onExtras = { onChange(value.copy(extras = it)) },
        growth = if (offerGrowth) value.growth else null,
        onGrowth = if (offerGrowth) {
            { setting ->
                onChange(
                    value.copy(
                        growth = setting,
                        // Growing begins at its start length, and the field above
                        // stops being editable — so the two must agree at once
                        // rather than after the next save.
                        duration = setting?.start ?: value.duration,
                    ),
                )
            }
        } else {
            null
        },
        growthIssues = growthIssues,
        offerQuantity = true,
        subtasks = groupName?.let { SubtaskSetting(it, value.subtasks) },
        onSubtasks = onGroupName?.let { rename ->
            { setting: SubtaskSetting ->
                rename(setting.groupName)
                onChange(
                    value.copy(
                        subtasks = setting.subtasks,
                        // A folded task's minutes move into the group at once, so
                        // the length on screen is the one that will be saved.
                        duration = value.duration + setting.folded.addedDuration(),
                        foldedItemIds = value.foldedItemIds + setting.folded.map { it.itemId },
                    ),
                )
            }
        },
        foldCandidates = foldCandidates,
    )
}

/** How far out a freshly ticked end date sits until it is moved: four weeks. */
private const val DEFAULT_REPEAT_DAYS = 28

/**
 * "Wiederholen bis": the last day a standing task still happens on.
 *
 * In the form itself rather than behind "Extras", because it answers the same
 * question the weekdays do — when does this happen — only from the other end.
 * A day stepper with week buttons, the shape every date in this app is asked
 * in, there being no date picker. It stops at today: a task that ends in the
 * past is ended, and "Beenden" is the button for that.
 */
@Composable
private fun RepeatUntilRow(extras: ItemExtras, onExtras: (ItemExtras) -> Unit) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val until = extras.repeatUntil
    val inDays = until?.let { today.daysUntil(it).coerceAtLeast(0) } ?: DEFAULT_REPEAT_DAYS

    fun set(days: Int) {
        onExtras(extras.copy(repeatUntil = today.plus(DatePeriod(days = days.coerceAtLeast(0)))))
    }

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        CheckRow(
            checked = until != null,
            onCheckedChange = { on ->
                if (on) set(inDays) else onExtras(extras.copy(repeatUntil = null))
            },
            label = "Wiederholen bis",
            hint = "Ein Enddatum. Der Tag selbst zählt noch mit; danach wird nichts " +
                "mehr eingeplant und die Aufgabe verschwindet aus den Listen.",
        )
        if (until != null) {
            EtaField(label = "Letzter Tag", hint = until.formatLong()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaStepper(
                        value = when (inDays) {
                            0 -> "heute"
                            1 -> "morgen"
                            else -> "in $inDays Tagen"
                        },
                        valueWidth = 104.dp,
                        onDecrement = { set(inDays - 1) },
                        onIncrement = { set(inDays + 1) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        EtaButton(
                            text = "− 1 Woche",
                            style = EtaButtonStyle.Secondary,
                            onClick = { set(inDays - 7) },
                        )
                        EtaButton(
                            text = "+ 1 Woche",
                            style = EtaButtonStyle.Secondary,
                            onClick = { set(inDays + 7) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Which other standing tasks the answers run into.
 *
 * A warning, not a refusal, and it says exactly where: a walk during a phone call
 * is two things at once on purpose, and only the user knows which collisions are
 * of that kind. Updated as the form changes, so moving the time until it goes
 * away is the way to answer it — by hand, or with **Nächster freier Slot**,
 * which names the hour it would move to before it is pressed: a button that
 * moved a task somewhere unannounced would be the form deciding for the user.
 */
@Composable
fun OverlapWarning(
    overlaps: List<RecurringOverlap>,
    /** Null where the form has no week to search; a null answer means no room left. */
    findNextFree: (() -> LocalTime?)? = null,
    onMoveTo: (LocalTime) -> Unit = {},
) {
    if (overlaps.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
        EtaText(
            text = if (overlaps.size == 1) {
                "Überschneidet sich mit einer anderen wiederkehrenden Aufgabe:"
            } else {
                "Überschneidet sich mit ${overlaps.size} anderen wiederkehrenden Aufgaben:"
            },
            style = EtaTheme.typography.bodyStrong,
            color = EtaTheme.colors.warning,
        )
        overlaps.forEach { overlap ->
            val other = overlap.other
            val time = other.startTime?.let { start ->
                val end = other.estimatedDuration?.let { start.plusMinutes(it.inWholeMinutes.toInt()) }
                if (end != null) "${start.formatClock()}–${end.formatClock()}" else start.formatClock()
            }.orEmpty()
            EtaText(
                text = "· ${other.name} — ${formatWeekdays(overlap.weekdays)}, $time",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )
        }
        if (findNextFree != null) {
            val free = findNextFree()
            if (free != null) {
                EtaButton(
                    text = "Nächster freier Slot · ${free.formatClock()}",
                    style = EtaButtonStyle.Secondary,
                    onClick = { onMoveTo(free) },
                )
            } else {
                EtaText(
                    text = "Später am Tag ist an diesen Wochentagen kein Slot mehr frei.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
        }
    }
}

/** A clock time moved on by [minutes], stopping at the end of the day. */
private fun LocalTime.plusMinutes(minutes: Int): LocalTime {
    val total = (hour * 60 + minute + minutes).coerceAtMost(24 * 60 - 1)
    return LocalTime(total / 60, total % 60)
}

/**
 * What a calendar appointment is asked, and what it is not.
 *
 * **No priority and no target date.** Both questions have already been answered,
 * and more firmly than the app could: the appointment stands on a named day at a
 * named hour. Asking for a priority would invite an answer that contradicts that,
 * and the revolver — the one thing priorities are for — never sees an imported
 * card anyway, since it arrives already placed.
 *
 * The time *is* asked, because the calendar's answer may be wrong for the app's
 * purposes: an all-day event carries no hour at all and arrives with a suggestion
 * rather than a fact, which `Planungsphase.md` asks for by name.
 */
data class AppointmentAttributes(
    val category: Category = Category.FOKUS,
    val start: LocalTime = LocalTime(9, 0),
    val duration: Duration = DEFAULT_DURATION,
    val travelBefore: Duration? = null,
    val returnAfter: Duration? = null,
    val breakAfter: Duration? = null,
    val endSound: Boolean = true,
    val extras: ItemExtras = ItemExtras(),
)

@Composable
fun AppointmentAttributeFields(
    value: AppointmentAttributes,
    onChange: (AppointmentAttributes) -> Unit,
    /** An all-day event: the time below is a suggestion, and says so. */
    allDay: Boolean = false,
) {
    EtaField(label = "Kategorie") {
        EtaChoice(
            options = CATEGORY_OPTIONS,
            selected = value.category,
            onSelect = { onChange(value.copy(category = it)) },
        )
    }
    EtaField(
        label = "Beginn",
        hint = if (allDay) {
            "Der Termin ist ganztägig eingetragen. Ohne eine Uhrzeit würde er den " +
                "ganzen Tag belegen — also bitte hier den wirklichen Rahmen setzen."
        } else {
            "Aus dem Kalender übernommen. Korrigierbar, falls der Eintrag schludrig ist."
        },
    ) {
        EtaTimePicker(
            value = value.start,
            onValueChange = { onChange(value.copy(start = it)) },
        )
    }
    EtaField(label = "Dauer") {
        EtaDurationPicker(
            value = value.duration,
            onValueChange = { onChange(value.copy(duration = it)) },
            minimum = MIN_TASK_DURATION,
        )
    }

    ExtrasBox(
        endSound = value.endSound,
        onEndSound = { onChange(value.copy(endSound = it)) },
        travel = value.travelBefore,
        onTravel = { onChange(value.copy(travelBefore = it)) },
        returnAfter = value.returnAfter,
        onReturnAfter = { onChange(value.copy(returnAfter = it)) },
        breakAfter = value.breakAfter,
        onBreakAfter = { onChange(value.copy(breakAfter = it)) },
        extras = value.extras,
        onExtras = { onChange(value.copy(extras = it)) },
    )
}

/**
 * Everything a card can be given beyond its own schedule, behind one fold.
 *
 * The box exists because the list kept growing. Anfahrt, Rückweg, Pause and the
 * end sound were four checkboxes standing between "Dauer" and the Sichern
 * button; the pomodoro rhythm and the reminder make six, and a growth task
 * makes seven. Every one of them is something most cards do not have, so they
 * belong behind a fold that says how many of them are on — folded away, the
 * header is the only thing that has to be read.
 *
 * The two journeys and the break are defaults for the blocks a definition will
 * produce rather than part of the task itself; the block carries them, and
 * dragging the task drags them with it. Pomodoro is the same shape, which is why
 * it belongs here rather than only on the long press it used to live behind.
 */
@Composable
fun ExtrasBox(
    endSound: Boolean,
    onEndSound: (Boolean) -> Unit,
    travel: Duration?,
    onTravel: (Duration?) -> Unit,
    returnAfter: Duration?,
    onReturnAfter: (Duration?) -> Unit,
    breakAfter: Duration?,
    onBreakAfter: (Duration?) -> Unit,
    extras: ItemExtras,
    onExtras: (ItemExtras) -> Unit,
    /** Null where growth is not on offer — a ToDo or an appointment cannot grow. */
    growth: GrowthSetting? = null,
    onGrowth: ((GrowthSetting?) -> Unit)? = null,
    growthIssues: List<GrowthFitIssue> = emptyList(),
    /**
     * Whether the Mengen-Inkrement is on offer. Only on a standing task: the
     * count grows with every confirmed completion, and a ToDo is completed once.
     */
    offerQuantity: Boolean = false,
    /**
     * Null where a deadline is not on offer, and the date the stepper counts
     * from otherwise.
     *
     * A standing task is never "done by" a date — it simply goes on — and an
     * appointment has already been given the day and hour it stands on. A ToDo is
     * the card a deadline is *about*, so it is the one that is asked.
     */
    today: LocalDate? = null,
    /**
     * The steps inside this card, where the screen that mounts the form can save
     * them. Null is "not on offer" — the same gating idiom as [today] and
     * [onGrowth] — and the two travel together because the builder edits the
     * group's name as well as its list.
     */
    subtasks: SubtaskSetting? = null,
    onSubtasks: ((SubtaskSetting) -> Unit)? = null,
    foldCandidates: List<FoldCandidate> = emptyList(),
) {
    val active = listOf(
        endSound,
        travel != null,
        returnAfter != null,
        breakAfter != null,
        extras.hasPomodoro,
        extras.hasReminder,
        growth != null,
        extras.hasQuantity,
        extras.hasDeadline,
        subtasks?.subtasks?.isNotEmpty() == true,
    ).count { it }

    EtaExpander(
        label = "Extras",
        hint = "Wege, Pause, Töne, Pomodoro, Erinnerung" +
            (if (today != null) ", Deadline" else "") +
            (if (onSubtasks != null) ", Subtasks" else "") +
            (if (offerQuantity) ", Mengen-Inkrement" else "") +
            if (onGrowth != null) " und Growth-Task." else ".",
        summary = when (active) {
            0 -> "Keine — antippen zum Öffnen."
            1 -> "1 aktiv"
            else -> "$active aktiv"
        },
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
            label = "Rückweg",
            hint = "Der Weg zurück, direkt hinter der Aufgabe und noch vor der Pause.",
            value = returnAfter,
            onValueChange = onReturnAfter,
        )
        MarginRow(
            label = "Pause danach",
            hint = "Liegt ganz am Ende. Passt sie an keiner Stelle des Tages, wird beim " +
                "Einplanen gefragt — die Wege selbst nie.",
            value = breakAfter,
            onValueChange = onBreakAfter,
        )
        PomodoroRow(extras = extras, onExtras = onExtras)
        ReminderRow(extras = extras, onExtras = onExtras)
        if (today != null) {
            DeadlineRow(today = today, extras = extras, onExtras = onExtras)
        }
        if (onGrowth != null) {
            GrowthRow(growth = growth, onGrowth = onGrowth, issues = growthIssues)
        }
        if (offerQuantity) {
            QuantityRow(extras = extras, onExtras = onExtras)
        }
        if (subtasks != null && onSubtasks != null) {
            SubtaskSection(
                setting = subtasks,
                onChange = onSubtasks,
                candidates = foldCandidates,
                routineMode = extras.routineMode,
                onRoutineMode = { onExtras(extras.copy(routineMode = it)) },
            )
        }
    }
}

/**
 * The "Subtasks" sub-fold, and the one button in it.
 *
 * A fold of its own inside Extras rather than another checkbox row: what is behind
 * it is a whole menu, and the count belongs on the outside so a card's steps can
 * be read without opening anything. The names are listed here too — the number
 * alone says how many there are and nothing about what they are.
 */
@Composable
private fun SubtaskSection(
    setting: SubtaskSetting,
    onChange: (SubtaskSetting) -> Unit,
    candidates: List<FoldCandidate>,
    routineMode: Boolean,
    onRoutineMode: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    EtaExpander(
        label = "Subtasks",
        hint = "Schritte innerhalb der Aufgabe, zum Abhaken während sie läuft.",
        summary = when (val count = setting.subtasks.size) {
            0 -> "Keine"
            1 -> "1 Schritt"
            else -> "$count Schritte"
        },
    ) {
        setting.subtasks.forEachIndexed { index, subtask ->
            EtaText(
                text = "${index + 1}. ${subtask.name}",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
        }
        EtaButton(
            text = "Subtasks erstellen / bearbeiten",
            style = EtaButtonStyle.Secondary,
            onClick = { open = true },
        )
        CheckRow(
            checked = routineMode,
            onCheckedChange = onRoutineMode,
            label = "Routine-Modus",
            hint = "Die Schritte werden der Reihe nach abgearbeitet: „Gerade“ zeigt " +
                "immer nur den anstehenden, und Abhaken hält die Uhrzeit fest.",
        )
    }

    if (open) {
        SubtaskBuilderDialog(
            setting = setting,
            candidates = candidates,
            onDismiss = { open = false },
            onSave = {
                onChange(it)
                open = false
            },
        )
    }
}

/** How far out a freshly ticked deadline sits until it is moved. */
private const val DEFAULT_DEADLINE_DAYS = 7

/** And at what hour: the end of an ordinary day, not the end of the calendar one. */
private val DEFAULT_DEADLINE_TIME = LocalTime(18, 0)

/**
 * A date and an hour by which the card has to be done.
 *
 * Stored as an [Instant] on the item, in the `deadlineAt` column that
 * `ItemType.DEADLINE` never used — see [ItemExtras]. Asked for as a day stepper
 * and a clock, which is the same shape the Erinnerungen tab uses, and for the
 * same reason: there is no date picker in this project to reach for, and a
 * deadline a month out would otherwise be thirty taps away.
 *
 * The stepper stops at today. A deadline in the past is not a deadline, it is a
 * missed one, and there is no reason to let one be *entered*.
 */
@Composable
private fun DeadlineRow(
    today: LocalDate,
    extras: ItemExtras,
    onExtras: (ItemExtras) -> Unit,
) {
    val zone = TimeZone.currentSystemDefault()
    val at = extras.deadlineAt?.toLocalDateTime(zone)
    val date = at?.date ?: today.plus(DatePeriod(days = DEFAULT_DEADLINE_DAYS))
    val time = at?.time ?: DEFAULT_DEADLINE_TIME
    val inDays = today.daysUntil(date).coerceAtLeast(0)

    fun set(newDate: LocalDate, newTime: LocalTime) {
        onExtras(extras.copy(deadlineAt = LocalDateTime(newDate, newTime).toInstant(zone)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        CheckRow(
            checked = extras.hasDeadline,
            onCheckedChange = { on ->
                if (on) set(date, time) else onExtras(extras.copy(deadlineAt = null))
            },
            label = "Deadline",
            hint = "Bis wann es spätestens fertig sein muss. Läuft dann oben auf dem " +
                "Reiter „Heute“ mit.",
        )
        if (extras.hasDeadline) {
            EtaField(label = "Datum", hint = date.formatLong()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaStepper(
                        value = when (inDays) {
                            0 -> "heute"
                            1 -> "morgen"
                            else -> "in $inDays Tagen"
                        },
                        valueWidth = 104.dp,
                        onDecrement = {
                            set(today.plus(DatePeriod(days = (inDays - 1).coerceAtLeast(0))), time)
                        },
                        onIncrement = { set(today.plus(DatePeriod(days = inDays + 1)), time) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        EtaButton(
                            text = "− 1 Woche",
                            style = EtaButtonStyle.Secondary,
                            onClick = {
                                set(
                                    today.plus(DatePeriod(days = (inDays - 7).coerceAtLeast(0))),
                                    time,
                                )
                            },
                        )
                        EtaButton(
                            text = "+ 1 Woche",
                            style = EtaButtonStyle.Secondary,
                            onClick = { set(today.plus(DatePeriod(days = inDays + 7)), time) },
                        )
                    }
                }
            }
            EtaField(label = "Uhrzeit") {
                EtaTimePicker(value = time, onValueChange = { set(date, it) })
            }
        }
    }
}

/**
 * The pomodoro rhythm, set up before the task rather than during it.
 *
 * The long press on the "now" box stays what it was — one sitting, counted from
 * this minute. This is the standing answer: every block the task produces starts
 * with the rhythm, and the long press is then a correction to one day of it.
 */
@Composable
private fun PomodoroRow(extras: ItemExtras, onExtras: (ItemExtras) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        CheckRow(
            checked = extras.hasPomodoro,
            onCheckedChange = { on ->
                onExtras(
                    if (on) {
                        extras.copy(
                            pomodoroWork = DEFAULT_POMODORO_WORK,
                            pomodoroPause = DEFAULT_POMODORO_PAUSE,
                        )
                    } else {
                        extras.copy(pomodoroWork = null, pomodoroPause = null)
                    },
                )
            },
            label = "Pomodoro",
            hint = "Teilt die Aufgabe in Arbeit und Pause. Jede Pause meldet sich, " +
                "und jede Arbeitsphase nach der ersten.",
        )
        if (extras.hasPomodoro) {
            EtaField(label = "Arbeit") {
                EtaDurationPicker(
                    value = extras.pomodoroWork ?: DEFAULT_POMODORO_WORK,
                    onValueChange = {
                        onExtras(extras.copy(pomodoroWork = it.inWholeMinutes.minutes))
                    },
                    step = 5.minutes,
                    minimum = MIN_POMODORO_PHASE,
                    precise = true,
                )
            }
            EtaField(label = "Pause") {
                EtaDurationPicker(
                    value = extras.pomodoroPause ?: DEFAULT_POMODORO_PAUSE,
                    onValueChange = {
                        onExtras(extras.copy(pomodoroPause = it.inWholeMinutes.minutes))
                    },
                    step = 5.minutes,
                    minimum = MIN_POMODORO_PHASE,
                    precise = true,
                )
            }
        }
    }
}

/**
 * The reminder: a number of hours before the task, and something to say.
 *
 * Counted from the **container**, so a task with a journey in front of it is
 * announced before setting off rather than before arriving — the hour is meant
 * as warning, and warning about a drive that has already started is not one.
 *
 * What it produces is an ordinary row in the Erinnerungen tab, which is what the
 * user asked for: one per task, aimed at its next occurrence, editable and
 * removable there like any other.
 */
@Composable
private fun ReminderRow(extras: ItemExtras, onExtras: (ItemExtras) -> Unit) {
    var typingHours by remember { mutableStateOf(false) }
    val hours = extras.reminderLeadHours ?: DEFAULT_REMINDER_LEAD_HOURS

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        CheckRow(
            checked = extras.hasReminder,
            onCheckedChange = { on ->
                onExtras(
                    extras.copy(
                        reminderLeadHours = if (on) DEFAULT_REMINDER_LEAD_HOURS else null,
                        reminderMessage = if (on) extras.reminderMessage else null,
                    ),
                )
            },
            label = "Erinnerung",
            hint = "Meldet sich vor dem Beginn — bei einer Anfahrt vor der Anfahrt. " +
                "Steht danach im Reiter Erinnerungen.",
        )
        if (extras.hasReminder) {
            EtaField(label = "Vorlauf") {
                EtaStepper(
                    value = if (hours == 1) "1 Stunde" else "$hours Stunden",
                    valueWidth = 96.dp,
                    onDecrement = {
                        onExtras(
                            extras.copy(
                                reminderLeadHours = (hours - 1)
                                    .coerceAtLeast(MIN_REMINDER_LEAD_HOURS),
                            ),
                        )
                    },
                    onIncrement = {
                        onExtras(
                            extras.copy(
                                reminderLeadHours = (hours + 1)
                                    .coerceAtMost(MAX_REMINDER_LEAD_HOURS),
                            ),
                        )
                    },
                    // Twelve taps to say "twelve hours before" is not an answer.
                    onValueClick = { typingHours = true },
                )
            }
            EtaField(label = "Botschaft") {
                EtaTextField(
                    value = extras.reminderMessage.orEmpty(),
                    onValueChange = { onExtras(extras.copy(reminderMessage = it)) },
                    placeholder = "Woran genau soll erinnert werden?",
                    singleLine = false,
                )
            }
        }
    }

    if (typingHours) {
        HoursDialog(
            hours = hours,
            onDismiss = { typingHours = false },
            onConfirm = {
                onExtras(extras.copy(reminderLeadHours = it))
                typingHours = false
            },
        )
    }
}

/** Typing the lead time rather than stepping to it. */
@Composable
private fun HoursDialog(
    hours: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(hours.toString()) }
    val parsed = text.trim().toIntOrNull()

    EtaDialog(title = "Wie viele Stunden vorher?", onDismiss = onDismiss) {
        EtaField(
            label = "Stunden",
            hint = "$MIN_REMINDER_LEAD_HOURS bis $MAX_REMINDER_LEAD_HOURS.",
        ) {
            EtaTextField(value = text, onValueChange = { text = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.width(EtaTheme.spacing.sm))
            EtaButton(
                text = "Übernehmen",
                enabled = parsed != null,
                onClick = {
                    parsed?.let {
                        onConfirm(it.coerceIn(MIN_REMINDER_LEAD_HOURS, MAX_REMINDER_LEAD_HOURS))
                    }
                },
            )
        }
    }
}

/**
 * The Growth-Task switch and the three lengths behind it.
 *
 * Three lengths and not three clock times: what grows is how long the task runs,
 * and the start time stays the start time — which with dynamic placement becomes
 * the *earliest* start time. See `GrowthTask.kt`.
 */
@Composable
private fun GrowthRow(
    growth: GrowthSetting?,
    onGrowth: (GrowthSetting?) -> Unit,
    issues: List<GrowthFitIssue>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        CheckRow(
            checked = growth != null,
            onCheckedChange = { on -> onGrowth(if (on) GrowthSetting.Default else null) },
            label = "Growth-Task",
            hint = "Wird mit bestätigten Abschlüssen länger, bis die Zielzeit " +
                "erreicht ist.",
        )
        if (growth == null) return@Column

        EtaField(
            label = "Startzeit",
            hint = "So lang beginnt die Aufgabe. Antippen, um sie sekundengenau einzugeben.",
        ) {
            EtaDurationPicker(
                value = growth.start,
                onValueChange = { start ->
                    onGrowth(
                        growth.copy(
                            start = start,
                            target = maxOf(growth.target, start),
                        ),
                    )
                },
                step = 1.minutes,
                minimum = MIN_GROWTH_DURATION,
                precise = true,
            )
        }
        EtaField(label = "Zielzeit", hint = "Hier hört das Wachsen auf.") {
            EtaDurationPicker(
                value = growth.target,
                onValueChange = { onGrowth(growth.copy(target = maxOf(it, growth.start))) },
                step = 1.minutes,
                minimum = MIN_GROWTH_DURATION,
                precise = true,
            )
        }
        EtaField(label = "Inkrement", hint = "Was ein Schritt hinzufügt.") {
            EtaDurationPicker(
                value = growth.increment,
                onValueChange = { onGrowth(growth.copy(increment = it)) },
                step = 1.minutes,
                minimum = MIN_GROWTH_INCREMENT,
                precise = true,
            )
        }
        UpdateConditionField(every = growth.every, onEvery = { onGrowth(growth.copy(every = it)) })
        CheckRow(
            checked = growth.dynamic,
            onCheckedChange = { onGrowth(growth.copy(dynamic = it)) },
            label = "Dynamische Zeitsetzung",
            hint = "Der Beginn gilt als früheste Uhrzeit. Ist sie belegt, rückt die " +
                "Aufgabe direkt dahinter — bei Streit entscheidet die Reihenfolge im " +
                "Reiter Growth-Tasks.",
        )

        GrowthFitWarning(issues)
    }
}

/**
 * The update condition: "Inkrement alle X Absolvierungen".
 *
 * Shared by the growth task and the Mengen-Inkrement, which ask it in the same
 * words. Counted is every completion the evening confirms — a tick alone is not
 * one, exactly as for the harvest.
 */
@Composable
private fun UpdateConditionField(every: Int, onEvery: (Int) -> Unit) {
    EtaField(
        label = "Update-Kondition",
        hint = "Gezählt wird jeder am Abend bestätigte Abschluss.",
    ) {
        EtaCountPicker(
            value = every,
            onValueChange = onEvery,
            range = DEFAULT_EVERY..MAX_EVERY,
            format = { if (it == 1) "jedes Mal" else "alle $it" },
            title = "Inkrement alle wie viele Abschlüsse?",
        )
    }
}

/**
 * The Mengen-Inkrement: a count that grows the way a growth task's length does.
 *
 * "Jeden Montag und Mittwoch X Klimmzüge, beginnend mit 1": a start, a step, the
 * update condition and — optionally — a count at which it stops. The length of
 * the task is not touched; the count is shown on every occurrence where the note
 * would be, only louder. See `Quantity.kt`.
 */
@Composable
private fun QuantityRow(extras: ItemExtras, onExtras: (ItemExtras) -> Unit) {
    val quantity = extras.quantity
    fun set(setting: QuantitySetting) = onExtras(extras.copy(quantity = setting))

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        CheckRow(
            checked = quantity != null,
            onCheckedChange = { on ->
                onExtras(extras.copy(quantity = if (on) QuantitySetting.Default else null))
            },
            label = "Mengen-Inkrement",
            hint = "Eine Anzahl, die mit bestätigten Abschlüssen wächst — etwa " +
                "Klimmzüge. Steht bei jedem Termin wie eine Notiz, nur deutlicher.",
        )
        if (quantity == null) return@Column

        EtaField(label = "Startanzahl", hint = "Damit beginnt die Aufgabe.") {
            EtaCountPicker(
                value = quantity.start,
                onValueChange = { start ->
                    set(
                        quantity.copy(
                            start = start,
                            target = quantity.target?.let { maxOf(it, start) },
                        ),
                    )
                },
                range = MIN_QUANTITY..QUANTITY_LIMIT,
            )
        }
        EtaField(label = "Inkrement", hint = "Was ein Schritt hinzufügt.") {
            EtaCountPicker(
                value = quantity.increment,
                onValueChange = { set(quantity.copy(increment = it)) },
                range = MIN_QUANTITY..QUANTITY_LIMIT,
                format = { "+$it" },
            )
        }
        UpdateConditionField(every = quantity.every, onEvery = { set(quantity.copy(every = it)) })
        CheckRow(
            checked = quantity.target != null,
            onCheckedChange = { on ->
                set(
                    quantity.copy(
                        target = if (on) quantity.start + DEFAULT_QUANTITY_SPAN * quantity.increment else null,
                    ),
                )
            },
            label = "Zielanzahl",
            hint = "Hier hört das Zählen auf. Ohne Ziel wächst die Anzahl weiter.",
        )
        quantity.target?.let { target ->
            EtaCountPicker(
                value = target,
                onValueChange = { set(quantity.copy(target = maxOf(it, quantity.start))) },
                range = quantity.start..QUANTITY_LIMIT,
            )
        }
    }
}

/** A ceiling for the steppers, far above any count anybody does in one sitting. */
private const val QUANTITY_LIMIT = 9_999

/** A freshly ticked target: ten steps above the start. */
private const val DEFAULT_QUANTITY_SPAN = 10

/**
 * That the final form of a growth task will not fit.
 *
 * A warning, never a refusal: the day may well be rearranged long before the
 * target is reached, and only the user knows whether it will be. Said here,
 * while the form is still open, because in four weeks it is no longer a decision
 * but a surprise.
 */
@Composable
fun GrowthFitWarning(issues: List<GrowthFitIssue>) {
    if (issues.isEmpty()) return
    val days = formatWeekdays(issues.map { it.weekday }.toSet())
    val noRoom = issues.any { it.problem == GrowthFitProblem.NO_ROOM }

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
        EtaText(
            text = "In ihrer Endform passt die Aufgabe nicht in den Tag: $days.",
            style = EtaTheme.typography.bodyStrong,
            color = EtaTheme.colors.warning,
        )
        EtaText(
            text = if (noRoom) {
                "Ab der angegebenen Uhrzeit bleibt kein Platz für die Zielzeit samt " +
                    "Pause. Eine frühere Uhrzeit, eine kürzere Zielzeit oder ein " +
                    "Wochentag weniger lösen das."
            } else {
                "Zur festen Uhrzeit steht dann etwas anderes im Weg. Mit dynamischer " +
                    "Zeitsetzung würde die Aufgabe stattdessen dahinter rücken."
            },
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.warning,
        )
    }
}

/** One margin: off, or a length. Ticking it starts at a quarter of an hour. */
@Composable
private fun MarginRow(
    label: String,
    hint: String,
    value: Duration?,
    onValueChange: (Duration?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        CheckRow(
            checked = value != null,
            onCheckedChange = { onValueChange(if (it) DEFAULT_MARGIN else null) },
            label = label,
            hint = hint,
        )
        if (value != null) {
            EtaDurationPicker(
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
 * Its own thing rather than an [EtaField], because what it labels is the box
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
        EtaCheckbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(EtaTheme.spacing.md))
        Column {
            EtaText(text = label, style = EtaTheme.typography.bodyStrong)
            EtaText(
                text = hint,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
    }
}

/**
 * These answers as the edit `RecurringTaskService` writes.
 *
 * For the screens that **create** a standing task from a button — the Listen
 * tab's long press and the Belohn-o-mat's binding menu.
 */
fun RecurringAttributes.toEdit(name: String, note: String?) = RecurringEdit(
    name = name,
    note = note,
    category = category,
    weekdays = weekdays,
    startTime = startTime,
    startTimes = ownStartTimes,
    duration = duration,
    travelBefore = travelBefore,
    returnAfter = returnAfter,
    breakAfter = breakAfter,
    endSound = endSound,
    extras = extras,
    growth = growth,
    subtasks = subtasks,
    folded = foldedItemIds,
)
