package com.example.eta.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.eta.domain.model.WeekParity
import com.example.eta.domain.setup.DailySlot
import com.example.eta.domain.setup.HousekeepingPlan
import com.example.eta.domain.setup.MealPlan
import com.example.eta.domain.setup.MindfulnessPlan
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.TimeSpan
import com.example.eta.domain.setup.WorkBlock
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.setup.WeeklySlot
import com.example.eta.domain.setup.WorkSchedule
import com.example.eta.domain.setup.bedPrepDuration
import com.example.eta.domain.setup.sleepDuration
import com.example.eta.domain.setup.suggestedWeekendNight
import com.example.eta.domain.setup.weekdayNight
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTimePicker
import com.example.eta.ui.components.EtaWeekdayPicker
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatMinutes
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatWeekdays
import com.example.eta.ui.theme.EtaTheme
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
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
                EtaText(text = title, style = EtaTheme.typography.title)
                if (description != null) {
                    EtaText(
                        text = description,
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textSecondary,
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
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
        EtaField(label = "Wochentage") {
            EtaWeekdayPicker(
                selected = slot.weekdays,
                onToggle = { day ->
                    val days = if (day in slot.weekdays) slot.weekdays - day else slot.weekdays + day
                    onChange(slot.copy(weekdays = days))
                },
                days = WEEK,
            )
        }
        EtaField(label = "Uhrzeit") {
            EtaTimePicker(
                value = slot.start,
                onValueChange = { onChange(slot.copy(start = it)) },
            )
        }
        EtaField(label = "Dauer") {
            EtaDurationPicker(
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
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
        EtaField(label = "Uhrzeit") {
            EtaTimePicker(value = slot.start, onValueChange = { onChange(slot.copy(start = it)) })
        }
        EtaField(label = "Dauer") {
            EtaDurationPicker(
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
        EtaText(
            text = "Der Rest deiner Zeit bleibt frei — das sind die Stunden, die du in der " +
                "Wochen- und Tagesplanung verplanst.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
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
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaField(label = "Bettfertig machen ab", hint = "Wird als tägliche Aufgabe angelegt.") {
                EtaTimePicker(
                    value = draft.bedPrepTime,
                    onValueChange = { time -> onChange { it.copy(bedPrepTime = time) } },
                )
            }
            EtaField(
                label = "Schlafen gehen",
                hint = AFTER_MIDNIGHT_HINT.takeIf { draft.weekdayNight.sleepsAfterMidnight },
            ) {
                EtaTimePicker(
                    value = draft.sleepTime,
                    onValueChange = { time -> onChange { it.copy(sleepTime = time) } },
                )
            }
            EtaField(label = "Aufstehen") {
                EtaTimePicker(
                    value = draft.wakeTime,
                    onValueChange = { time -> onChange { it.copy(wakeTime = time) } },
                )
            }
            EtaField(
                label = "Morgenroutine",
                hint = "Wie lange sie dauert, ab dem Aufstehen — Anziehen, Frühstück, " +
                    "Ankommen. Ihre Schritte legst du in den Einstellungen oder im " +
                    "Reiter Listen an.",
            ) {
                EtaDurationPicker(
                    value = draft.morningDuration,
                    onValueChange = { duration -> onChange { it.copy(morningDuration = duration) } },
                )
            }
            DerivedHint(
                "Nacht: ${draft.sleepDuration().formatShort()} · " +
                    "Bettfertig: ${draft.bedPrepDuration().formatShort()}",
            )

            val weekend = draft.weekendNight
            EtaField(
                label = "Am Wochenende",
                hint = "Eigene Schlafzeiten für die Tage, an denen du ausschlafen kannst.",
            ) {
                EtaChoice(
                    options = listOf(false to "Wie unter der Woche", true to "Andere Zeiten"),
                    selected = weekend != null,
                    onSelect = { differs ->
                        onChange {
                            it.copy(
                                weekendNight = if (differs) {
                                    it.weekendNight ?: it.suggestedWeekendNight()
                                } else {
                                    null
                                },
                            )
                        }
                    },
                )
            }
            if (weekend != null) {
                fun change(transform: (NightTimes) -> NightTimes) =
                    onChange { it.copy(weekendNight = it.weekendNight?.let(transform)) }

                // Which days those are is the user's to say: a week that works
                // Wednesday to Sunday has its weekend on Monday and Tuesday.
                val days = draft.weekendDays
                EtaField(
                    label = "Welche Tage sind dein Wochenende?",
                    hint = "Die Tage, an denen du nach diesen Zeiten aufstehst — der " +
                        "Abend davor gehört jeweils dazu.",
                ) {
                    EtaWeekdayPicker(
                        selected = days,
                        onToggle = { day ->
                            onChange {
                                val next = if (day in it.weekendDays) {
                                    it.weekendDays - day
                                } else {
                                    it.weekendDays + day
                                }
                                // A weekend of no days is "Wie unter der Woche",
                                // and that is the switch above.
                                if (next.isEmpty()) it else it.copy(weekendDays = next)
                            }
                        },
                        days = WEEK,
                    )
                }

                // The days are named beside each hour, because an hour alone
                // does not say which day's it is — least of all one after
                // midnight, which falls on the day of getting up.
                EtaField(
                    label = "Bettfertig machen ab (${formatWeekdays(weekend.bedPrepDays(days))})",
                ) {
                    EtaTimePicker(
                        value = weekend.bedPrep,
                        onValueChange = { time -> change { it.copy(bedPrep = time) } },
                    )
                }
                EtaField(
                    label = "Schlafen gehen (${formatWeekdays(weekend.sleepDays(days))})",
                    hint = AFTER_MIDNIGHT_HINT.takeIf { weekend.sleepsAfterMidnight },
                ) {
                    EtaTimePicker(
                        value = weekend.sleep,
                        onValueChange = { time -> change { it.copy(sleep = time) } },
                    )
                }
                EtaField(label = "Aufstehen (${formatWeekdays(days)})") {
                    EtaTimePicker(
                        value = weekend.wake,
                        onValueChange = { time -> change { it.copy(wake = time) } },
                    )
                }
                DerivedHint(
                    "Nacht am Wochenende: ${weekend.sleepDuration().formatShort()} · " +
                        "Bettfertig: ${weekend.bedPrepDuration().formatShort()}",
                )
            }
        }
    }
}

/**
 * Said under a bedtime that lies after midnight. Such an hour belongs to the
 * night it starts, not to the evening of the day it is written on: 01:00 for
 * the night into Sunday is Sunday at one, at the end of Saturday evening.
 */
private const val AFTER_MIDNIGHT_HINT =
    "Nach Mitternacht — zählt als Ende des Abends davor und liegt im Kalender " +
        "schon auf dem Tag des Aufstehens."

private const val MEAL_PREP = "prep"
private const val MEAL_DAILY = "daily"

@Composable
fun MealsStep(draft: UserSetup, onChange: OnSetupChange) {
    val plan = draft.meals
    StepCard(
        title = "Essen",
        description = "Wie kümmerst du dich ums Essen?",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaChoice(
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
                    verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
                ) {
                    plan.slots.forEachIndexed { index, slot ->
                        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                            EtaText(
                                text = "Kochzeit ${index + 1}",
                                style = EtaTheme.typography.heading,
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
                                EtaButton(
                                    text = "Kochzeit entfernen",
                                    style = EtaButtonStyle.Secondary,
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
                    EtaButton(
                        text = "Weitere Kochzeit",
                        style = EtaButtonStyle.Secondary,
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
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaChoice(
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
                EtaField(label = "In welchen Kalenderwochen?") {
                    EtaChoice(
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
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaChoice(
                options = listOf(false to "Nein", true to "Wöchentlich"),
                selected = slot != null,
                onSelect = { wants ->
                    onChange {
                        it.copy(
                            sport = if (wants) {
                                slot ?: WeeklySlot(DayOfWeek.TUESDAY, LocalTime(17, 15), 1.hours)
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
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaField(label = "Ab wann") {
                EtaTimePicker(
                    value = draft.freeTime.start,
                    onValueChange = { time ->
                        onChange { it.copy(freeTime = it.freeTime.copy(start = time)) }
                    },
                )
            }
            EtaField(label = "Wie lange") {
                EtaDurationPicker(
                    value = draft.freeTime.duration,
                    onValueChange = { duration ->
                        onChange { it.copy(freeTime = it.freeTime.copy(duration = duration)) }
                    },
                    minimum = 15.minutes,
                )
            }
            EtaField(
                label = "Soziale Interaktion pro Woche",
                hint = "Ohne feste Uhrzeit — wird von den freien Stunden abgezogen.",
            ) {
                EtaDurationPicker(
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
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaChoice(
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
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaChoice(
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
                    verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xl),
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
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
        EtaField(label = fromLabel) {
            EtaTimePicker(value = span.start, onValueChange = { onChange(span.copy(start = it)) })
        }
        EtaField(label = toLabel) {
            EtaTimePicker(value = span.end, onValueChange = { onChange(span.copy(end = it)) })
        }
        if (span.duration == Duration.ZERO) {
            EtaText(
                text = "Das Ende liegt vor dem Anfang — dieser Zeitraum wird ignoriert.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )
        }
    }
}

/** One stretch of work together with the break inside it. */
@Composable
private fun WorkBlockEditor(block: WorkBlock, onChange: (WorkBlock) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
        TimeSpanEditor(block.span) { span -> onChange(block.copy(span = span)) }

        EtaChoice(
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
                EtaText(
                    text = "Die Pause liegt nicht innerhalb der Arbeitszeit — sie wird " +
                        "so nicht eingeplant.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.warning,
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
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
        EtaText(text = weekday.formatLong(), style = EtaTheme.typography.heading)
        if (blocks.isEmpty()) {
            EtaText(
                text = "Frei.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textMuted,
            )
        }
        blocks.forEachIndexed { index, block ->
            WorkBlockEditor(block) { updated ->
                onChange(blocks.toMutableList().also { it[index] = updated })
            }
            EtaButton(
                text = "Zeitraum entfernen",
                style = EtaButtonStyle.Secondary,
                onClick = { onChange(blocks.filterIndexed { i, _ -> i != index }) },
            )
        }
        EtaButton(
            text = "Zeitraum hinzufügen",
            style = EtaButtonStyle.Secondary,
            onClick = { onChange(blocks + DEFAULT_WORK) },
        )
    }
}

@Composable
fun PlanningStep(draft: UserSetup, onChange: OnSetupChange) {
    StepCard(
        title = "Planungsphasen",
        description = "Zu diesen Zeiten meldet sich Eta, um den nächsten Tag " +
            "beziehungsweise die nächste Woche mit dir zu planen.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaField(label = "Tagesplanung", hint = "Jeden Abend zur selben Zeit.") {
                EtaTimePicker(
                    value = draft.dailyPlanningTime,
                    onValueChange = { time -> onChange { it.copy(dailyPlanningTime = time) } },
                )
            }
            EtaField(label = "Wochenplanung — Wochentag") {
                EtaWeekdayPicker(
                    selected = draft.weeklyPlanningDay,
                    onSelect = { day -> onChange { it.copy(weeklyPlanningDay = day) } },
                    days = WEEK,
                )
            }
            EtaField(label = "Wochenplanung — Uhrzeit") {
                EtaTimePicker(
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
        description = "Passt das so, legt Eta die wiederkehrenden Aufgaben an.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    EtaText(
                        text = "Freie Stunden pro Woche",
                        style = EtaTheme.typography.label,
                        color = EtaTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.size(EtaTheme.spacing.xs))
                    EtaText(
                        text = formatMinutes(outlook.freeMinutesPerWeek),
                        style = EtaTheme.typography.display,
                    )
                }
            }
            EtaText(
                text = "Das ist die Zeit, die in der Wochenplanung zu vergeben ist — " +
                    "abzüglich Schlaf, fester Termine und deiner Sozialzeit.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaText(
                text = "${outlook.generatedTaskCount} wiederkehrende Aufgaben werden angelegt.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
            if (outlook.conflicts.isEmpty()) {
                EtaText(
                    text = "Keine Doppeltbelegungen.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.success,
                )
            }
        }
    }
}

/** The little derived line under a group of answers. */
@Composable
private fun DerivedHint(text: String) {
    EtaText(
        text = text,
        style = EtaTheme.typography.caption,
        color = EtaTheme.colors.textMuted,
    )
}
