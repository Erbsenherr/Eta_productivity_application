package com.example.erik_iteration_2.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.erik_iteration_2.domain.model.WeekParity
import com.example.erik_iteration_2.domain.setup.DailySlot
import com.example.erik_iteration_2.domain.setup.HousekeepingPlan
import com.example.erik_iteration_2.domain.setup.MealPlan
import com.example.erik_iteration_2.domain.setup.MindfulnessPlan
import com.example.erik_iteration_2.domain.setup.TimeSpan
import com.example.erik_iteration_2.domain.setup.WorkBlock
import com.example.erik_iteration_2.domain.setup.UserSetup
import com.example.erik_iteration_2.domain.setup.WEEK
import com.example.erik_iteration_2.domain.setup.WeeklySlot
import com.example.erik_iteration_2.domain.setup.WorkSchedule
import com.example.erik_iteration_2.domain.setup.bedPrepDuration
import com.example.erik_iteration_2.domain.setup.sleepDuration
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikChoice
import com.example.erik_iteration_2.ui.components.ErikDurationPicker
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTimePicker
import com.example.erik_iteration_2.ui.components.ErikWeekdayPicker
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.format.formatMinutes
import com.example.erik_iteration_2.ui.format.formatShort
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** Shorthand for the answer callbacks: every step edits the one draft. */
typealias OnSetupChange = ((UserSetup) -> UserSetup) -> Unit

@Composable
private fun StepCard(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.xs)) {
                ErikText(text = title, style = ErikTheme.typography.title)
                if (description != null) {
                    ErikText(
                        text = description,
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.textSecondary,
                    )
                }
            }
            content()
        }
    }
}

/** A slot the user can place: weekdays, time, duration. */
@Composable
private fun WeeklySlotEditor(
    slot: WeeklySlot,
    onChange: (WeeklySlot) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
        ErikField(label = "Wochentage") {
            ErikWeekdayPicker(
                selected = slot.weekdays,
                onToggle = { day ->
                    val days = if (day in slot.weekdays) slot.weekdays - day else slot.weekdays + day
                    onChange(slot.copy(weekdays = days))
                },
                days = WEEK,
            )
        }
        ErikField(label = "Uhrzeit") {
            ErikTimePicker(
                value = slot.start,
                onValueChange = { onChange(slot.copy(start = it)) },
            )
        }
        ErikField(label = "Dauer") {
            ErikDurationPicker(
                value = slot.duration,
                onValueChange = { onChange(slot.copy(duration = it)) },
                minimum = 15.minutes,
            )
        }
    }
}

@Composable
private fun DailySlotEditor(
    slot: DailySlot,
    onChange: (DailySlot) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
        ErikField(label = "Uhrzeit") {
            ErikTimePicker(value = slot.start, onValueChange = { onChange(slot.copy(start = it)) })
        }
        ErikField(label = "Dauer") {
            ErikDurationPicker(
                value = slot.duration,
                onValueChange = { onChange(slot.copy(duration = it)) },
                minimum = 15.minutes,
            )
        }
    }
}

@Composable
fun WelcomeStep() {
    StepCard(
        title = "Lass uns deine Woche aufspannen",
        description = "Ein paar Fragen zu deinem Alltag. Daraus entsteht das Grundgerüst " +
            "deiner Woche: Schlaf, Arbeit, Pausen und die wiederkehrenden Aufgaben. " +
            "Alles davon lässt sich später ändern.",
    ) {
        ErikText(
            text = "Der Rest deiner Zeit bleibt frei — das sind die Stunden, die du in der " +
                "Wochen- und Tagesplanung verplanst.",
            style = ErikTheme.typography.body,
            color = ErikTheme.colors.textSecondary,
        )
    }
}

@Composable
fun SleepStep(draft: UserSetup, onChange: OnSetupChange) {
    StepCard(
        title = "Schlaf und Morgen",
        description = "Schlafzeiten werden im Tagesplaner farblich hinterlegt und aus den " +
            "freien Stunden herausgerechnet.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            ErikField(label = "Bettfertig machen ab", hint = "Wird als tägliche Aufgabe angelegt.") {
                ErikTimePicker(
                    value = draft.bedPrepTime,
                    onValueChange = { time -> onChange { it.copy(bedPrepTime = time) } },
                )
            }
            ErikField(label = "Schlafen gehen") {
                ErikTimePicker(
                    value = draft.sleepTime,
                    onValueChange = { time -> onChange { it.copy(sleepTime = time) } },
                )
            }
            ErikField(label = "Aufstehen") {
                ErikTimePicker(
                    value = draft.wakeTime,
                    onValueChange = { time -> onChange { it.copy(wakeTime = time) } },
                )
            }
            ErikField(
                label = "Zeit am Morgen",
                hint = "Ab dem Aufstehen — Anziehen, Frühstück, Ankommen.",
            ) {
                ErikDurationPicker(
                    value = draft.morningDuration,
                    onValueChange = { duration -> onChange { it.copy(morningDuration = duration) } },
                )
            }
            DerivedHint(
                "Nacht: ${draft.sleepDuration().formatShort()} · " +
                    "Bettfertig: ${draft.bedPrepDuration().formatShort()}",
            )
        }
    }
}

private const val MEAL_PREP = "prep"
private const val MEAL_DAILY = "daily"

@Composable
fun MealsStep(draft: UserSetup, onChange: OnSetupChange) {
    val plan = draft.meals
    StepCard(
        title = "Essen",
        description = "Wie kümmerst du dich ums Essen?",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            ErikChoice(
                options = listOf(
                    MEAL_PREP to "Mealprep — ein Koch- und Einkaufstag",
                    MEAL_DAILY to "Täglich kochen",
                ),
                selected = if (plan is MealPlan.MealPrep) MEAL_PREP else MEAL_DAILY,
                onSelect = { choice ->
                    onChange {
                        it.copy(
                            meals = when (choice) {
                                MEAL_PREP -> MealPlan.MealPrep(
                                    WeeklySlot(DayOfWeek.SUNDAY, LocalTime(11, 0), 3.hours),
                                )

                                else -> MealPlan.DailyCooking(
                                    listOf(DailySlot(LocalTime(18, 30), 1.hours)),
                                )
                            },
                        )
                    }
                },
            )

            when (plan) {
                is MealPlan.MealPrep -> WeeklySlotEditor(plan.slot) { slot ->
                    onChange { it.copy(meals = MealPlan.MealPrep(slot)) }
                }

                is MealPlan.DailyCooking -> Column(
                    verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
                ) {
                    plan.slots.forEachIndexed { index, slot ->
                        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                            ErikText(
                                text = "Kochzeit ${index + 1}",
                                style = ErikTheme.typography.heading,
                            )
                            DailySlotEditor(slot) { updated ->
                                onChange {
                                    it.copy(
                                        meals = MealPlan.DailyCooking(
                                            plan.slots.toMutableList().also { list ->
                                                list[index] = updated
                                            },
                                        ),
                                    )
                                }
                            }
                            if (plan.slots.size > 1) {
                                ErikButton(
                                    text = "Kochzeit entfernen",
                                    style = ErikButtonStyle.Secondary,
                                    onClick = {
                                        onChange {
                                            it.copy(
                                                meals = MealPlan.DailyCooking(
                                                    plan.slots.filterIndexed { i, _ -> i != index },
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    ErikButton(
                        text = "Weitere Kochzeit",
                        style = ErikButtonStyle.Secondary,
                        onClick = {
                            onChange {
                                it.copy(
                                    meals = MealPlan.DailyCooking(
                                        plan.slots + DailySlot(LocalTime(12, 0), 45.minutes),
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

private const val HOUSE_NONE = "none"
private const val HOUSE_WEEKLY = "weekly"
private const val HOUSE_BIWEEKLY = "biweekly"

@Composable
fun HousekeepingStep(draft: UserSetup, onChange: OnSetupChange) {
    val plan = draft.housekeeping
    val defaultSlot = WeeklySlot(DayOfWeek.SATURDAY, LocalTime(10, 0), 1.hours)
    StepCard(title = "Hausputz", description = "Soll regelmäßig geputzt werden?") {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            ErikChoice(
                options = listOf(
                    HOUSE_NONE to "Nein",
                    HOUSE_WEEKLY to "Wöchentlich",
                    HOUSE_BIWEEKLY to "Alle zwei Wochen",
                ),
                selected = when (plan) {
                    null -> HOUSE_NONE
                    is HousekeepingPlan.Weekly -> HOUSE_WEEKLY
                    is HousekeepingPlan.Biweekly -> HOUSE_BIWEEKLY
                },
                onSelect = { choice ->
                    onChange {
                        it.copy(
                            housekeeping = when (choice) {
                                HOUSE_NONE -> null
                                HOUSE_WEEKLY -> HousekeepingPlan.Weekly(plan?.slot ?: defaultSlot)
                                else -> HousekeepingPlan.Biweekly(
                                    plan?.slot ?: defaultSlot,
                                    WeekParity.EVEN,
                                )
                            },
                        )
                    }
                },
            )

            if (plan is HousekeepingPlan.Biweekly) {
                ErikField(label = "In welchen Kalenderwochen?") {
                    ErikChoice(
                        options = listOf(
                            WeekParity.EVEN to "Gerade KW",
                            WeekParity.ODD to "Ungerade KW",
                        ),
                        selected = plan.parity,
                        onSelect = { parity ->
                            onChange { it.copy(housekeeping = plan.copy(parity = parity)) }
                        },
                    )
                }
            }

            if (plan != null) {
                WeeklySlotEditor(plan.slot) { slot ->
                    onChange {
                        it.copy(
                            housekeeping = when (plan) {
                                is HousekeepingPlan.Weekly -> HousekeepingPlan.Weekly(slot)
                                is HousekeepingPlan.Biweekly -> plan.copy(slot = slot)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SportStep(draft: UserSetup, onChange: OnSetupChange) {
    val slot = draft.sport
    StepCard(title = "Sport", description = "Soll regelmäßig Sport gemacht werden?") {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            ErikChoice(
                options = listOf(false to "Nein", true to "Wöchentlich"),
                selected = slot != null,
                onSelect = { wants ->
                    onChange {
                        it.copy(
                            sport = if (wants) {
                                slot ?: WeeklySlot(DayOfWeek.TUESDAY, LocalTime(18, 0), 1.hours)
                            } else {
                                null
                            },
                        )
                    }
                },
            )
            if (slot != null) {
                WeeklySlotEditor(slot) { updated -> onChange { it.copy(sport = updated) } }
            }
        }
    }
}

@Composable
fun FreeTimeStep(draft: UserSetup, onChange: OnSetupChange) {
    StepCard(
        title = "Freie Zeit",
        description = "Die Mindestzeit pro Tag, die dir außerhalb der Pausen gehört. " +
            "Verzichtest du später darauf, schreibt dir das Punkte gut.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            ErikField(label = "Ab wann") {
                ErikTimePicker(
                    value = draft.freeTime.start,
                    onValueChange = { time ->
                        onChange { it.copy(freeTime = it.freeTime.copy(start = time)) }
                    },
                )
            }
            ErikField(label = "Wie lange") {
                ErikDurationPicker(
                    value = draft.freeTime.duration,
                    onValueChange = { duration ->
                        onChange { it.copy(freeTime = it.freeTime.copy(duration = duration)) }
                    },
                    minimum = 15.minutes,
                )
            }
            ErikField(
                label = "Soziale Interaktion pro Woche",
                hint = "Ohne feste Uhrzeit — wird von den freien Stunden abgezogen.",
            ) {
                ErikDurationPicker(
                    value = draft.socialTimePerWeek,
                    onValueChange = { duration ->
                        onChange { it.copy(socialTimePerWeek = duration) }
                    },
                    step = 30.minutes,
                )
            }
        }
    }
}

private const val MIND_NONE = "none"
private const val MIND_DAILY = "daily"
private const val MIND_WEEKLY = "weekly"

@Composable
fun MindfulnessStep(draft: UserSetup, onChange: OnSetupChange) {
    val plan = draft.mindfulness
    StepCard(
        title = "Selbstachtsamkeit",
        description = "Zeit für dich — Meditation, Journal, Spaziergang.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            ErikChoice(
                options = listOf(
                    MIND_NONE to "Nein",
                    MIND_DAILY to "Jeden Tag",
                    MIND_WEEKLY to "Einmal pro Woche",
                ),
                selected = when (plan) {
                    null -> MIND_NONE
                    is MindfulnessPlan.EveryDay -> MIND_DAILY
                    is MindfulnessPlan.Weekly -> MIND_WEEKLY
                },
                onSelect = { choice ->
                    onChange {
                        it.copy(
                            mindfulness = when (choice) {
                                MIND_NONE -> null
                                MIND_DAILY -> MindfulnessPlan.EveryDay(
                                    DailySlot(LocalTime(21, 30), 15.minutes),
                                )

                                else -> MindfulnessPlan.Weekly(
                                    WeeklySlot(DayOfWeek.SUNDAY, LocalTime(10, 0), 1.hours),
                                )
                            },
                        )
                    }
                },
            )

            when (plan) {
                is MindfulnessPlan.EveryDay -> DailySlotEditor(plan.slot) { slot ->
                    onChange { it.copy(mindfulness = MindfulnessPlan.EveryDay(slot)) }
                }

                is MindfulnessPlan.Weekly -> WeeklySlotEditor(plan.slot) { slot ->
                    onChange { it.copy(mindfulness = MindfulnessPlan.Weekly(slot)) }
                }

                null -> Unit
            }
        }
    }
}

private const val WORK_NONE = "none"
private const val WORK_UNIFORM = "uniform"
private const val WORK_PER_DAY = "perday"

private val DEFAULT_WORK = WorkBlock(
    span = TimeSpan(LocalTime(9, 0), LocalTime(17, 0)),
    pause = TimeSpan(LocalTime(12, 30), LocalTime(13, 15)),
)

/** A 45-minute break in the middle of the block, rounded onto the quarter hour. */
private fun WorkBlock.suggestedPause(): TimeSpan {
    val middle = (span.start.toSecondOfDay() + span.end.toSecondOfDay()) / 2
    val start = (middle / (15 * 60)) * (15 * 60)
    return TimeSpan(
        LocalTime.fromSecondOfDay(start.coerceAtMost(24 * 3600 - 45 * 60)),
        LocalTime.fromSecondOfDay((start + 45 * 60).coerceAtMost(24 * 3600 - 1)),
    )
}

@Composable
fun WorkStep(draft: UserSetup, onChange: OnSetupChange) {
    val work = draft.work
    StepCard(
        title = "Arbeit und Uni",
        description = "Feste Zeiten, die der Planer nicht anfassen darf. Die Pause " +
            "gehört mit dazu — daraus werden dann Arbeit, Pause, Arbeit.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            ErikChoice(
                options = listOf(
                    WORK_NONE to "Keine festen Zeiten",
                    WORK_UNIFORM to "Jeden Werktag gleich",
                    WORK_PER_DAY to "Für jeden Wochentag einzeln",
                ),
                selected = when (work) {
                    is WorkSchedule.None -> WORK_NONE
                    is WorkSchedule.EveryWorkday -> WORK_UNIFORM
                    is WorkSchedule.PerWeekday -> WORK_PER_DAY
                },
                onSelect = { choice ->
                    onChange {
                        it.copy(
                            work = when (choice) {
                                WORK_NONE -> WorkSchedule.None
                                WORK_UNIFORM -> WorkSchedule.EveryWorkday(DEFAULT_WORK)
                                else -> WorkSchedule.PerWeekday(
                                    WEEK.take(5).associateWith { listOf(DEFAULT_WORK) },
                                )
                            },
                        )
                    }
                },
            )

            when (work) {
                is WorkSchedule.None -> Unit

                is WorkSchedule.EveryWorkday -> WorkBlockEditor(
                    block = work.block,
                    onChange = { block ->
                        onChange { it.copy(work = WorkSchedule.EveryWorkday(block)) }
                    },
                )

                is WorkSchedule.PerWeekday -> Column(
                    verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.xl),
                ) {
                    WEEK.forEach { weekday ->
                        WeekdayWorkEditor(
                            weekday = weekday,
                            blocks = work.blocks[weekday].orEmpty(),
                            onChange = { blocks ->
                                onChange {
                                    it.copy(
                                        work = WorkSchedule.PerWeekday(
                                            work.blocks + (weekday to blocks),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSpanEditor(
    span: TimeSpan,
    fromLabel: String = "Von",
    toLabel: String = "Bis",
    onChange: (TimeSpan) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
        ErikField(label = fromLabel) {
            ErikTimePicker(value = span.start, onValueChange = { onChange(span.copy(start = it)) })
        }
        ErikField(label = toLabel) {
            ErikTimePicker(value = span.end, onValueChange = { onChange(span.copy(end = it)) })
        }
        if (span.duration == Duration.ZERO) {
            ErikText(
                text = "Das Ende liegt vor dem Anfang — dieser Zeitraum wird ignoriert.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.warning,
            )
        }
    }
}

/** One stretch of work together with the break inside it. */
@Composable
private fun WorkBlockEditor(block: WorkBlock, onChange: (WorkBlock) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
        TimeSpanEditor(block.span) { span -> onChange(block.copy(span = span)) }

        ErikChoice(
            options = listOf(false to "Ohne Pause", true to "Mit Pause"),
            selected = block.pause != null,
            onSelect = { wants ->
                onChange(block.copy(pause = if (wants) block.suggestedPause() else null))
            },
        )

        block.pause?.let { pause ->
            TimeSpanEditor(
                span = pause,
                fromLabel = "Pause von",
                toLabel = "Pause bis",
                onChange = { onChange(block.copy(pause = it)) },
            )
            if (block.hasUnusablePause()) {
                ErikText(
                    text = "Die Pause liegt nicht innerhalb der Arbeitszeit — sie wird " +
                        "so nicht eingeplant.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.warning,
                )
            }
        }
    }
}

@Composable
private fun WeekdayWorkEditor(
    weekday: DayOfWeek,
    blocks: List<WorkBlock>,
    onChange: (List<WorkBlock>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
        ErikText(text = weekday.formatLong(), style = ErikTheme.typography.heading)
        if (blocks.isEmpty()) {
            ErikText(
                text = "Frei.",
                style = ErikTheme.typography.body,
                color = ErikTheme.colors.textMuted,
            )
        }
        blocks.forEachIndexed { index, block ->
            WorkBlockEditor(block) { updated ->
                onChange(blocks.toMutableList().also { it[index] = updated })
            }
            ErikButton(
                text = "Zeitraum entfernen",
                style = ErikButtonStyle.Secondary,
                onClick = { onChange(blocks.filterIndexed { i, _ -> i != index }) },
            )
        }
        ErikButton(
            text = "Zeitraum hinzufügen",
            style = ErikButtonStyle.Secondary,
            onClick = { onChange(blocks + DEFAULT_WORK) },
        )
    }
}

@Composable
fun PlanningStep(draft: UserSetup, onChange: OnSetupChange) {
    StepCard(
        title = "Planungsphasen",
        description = "Zu diesen Zeiten meldet sich ERIK, um den nächsten Tag " +
            "beziehungsweise die nächste Woche mit dir zu planen.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            ErikField(label = "Tagesplanung", hint = "Jeden Abend zur selben Zeit.") {
                ErikTimePicker(
                    value = draft.dailyPlanningTime,
                    onValueChange = { time -> onChange { it.copy(dailyPlanningTime = time) } },
                )
            }
            ErikField(label = "Wochenplanung — Wochentag") {
                ErikWeekdayPicker(
                    selected = draft.weeklyPlanningDay,
                    onSelect = { day -> onChange { it.copy(weeklyPlanningDay = day) } },
                    days = WEEK,
                )
            }
            ErikField(label = "Wochenplanung — Uhrzeit") {
                ErikTimePicker(
                    value = draft.weeklyPlanningTime,
                    onValueChange = { time -> onChange { it.copy(weeklyPlanningTime = time) } },
                )
            }
        }
    }
}

@Composable
fun SummaryStep(outlook: SetupOutlook) {
    StepCard(
        title = "Das ist deine Woche",
        description = "Passt das so, legt ERIK die wiederkehrenden Aufgaben an.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    ErikText(
                        text = "Freie Stunden pro Woche",
                        style = ErikTheme.typography.label,
                        color = ErikTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.size(ErikTheme.spacing.xs))
                    ErikText(
                        text = formatMinutes(outlook.freeMinutesPerWeek),
                        style = ErikTheme.typography.display,
                    )
                }
            }
            ErikText(
                text = "Das ist die Zeit, die in der Wochenplanung zu vergeben ist — " +
                    "abzüglich Schlaf, fester Termine und deiner Sozialzeit.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
            ErikText(
                text = "${outlook.generatedTaskCount} wiederkehrende Aufgaben werden angelegt.",
                style = ErikTheme.typography.body,
                color = ErikTheme.colors.textSecondary,
            )
            if (outlook.conflicts.isEmpty()) {
                ErikText(
                    text = "Keine Doppeltbelegungen.",
                    style = ErikTheme.typography.body,
                    color = ErikTheme.colors.success,
                )
            }
        }
    }
}

/** The little derived line under a group of answers. */
@Composable
private fun DerivedHint(text: String) {
    ErikText(
        text = text,
        style = ErikTheme.typography.caption,
        color = ErikTheme.colors.textMuted,
    )
}
