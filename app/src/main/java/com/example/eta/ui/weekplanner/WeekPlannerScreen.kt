package com.example.eta.ui.weekplanner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.data.repository.InflationPreview
import com.example.eta.domain.model.Item
import com.example.eta.domain.planning.WeekBudget
import com.example.eta.domain.planning.costOf
import com.example.eta.ui.calendar.CalendarEventsSection
import com.example.eta.ui.calendar.CalendarEventsViewModel
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.components.ReportToTutorial
import com.example.eta.ui.components.tutorialAllows
import com.example.eta.domain.tutorial.TutorialGate
import com.example.eta.domain.tutorial.TutorialSignal
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaProgressBar
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatMinutes
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.subtasks.foldCandidatesExcept
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.theme.colorOf
import kotlinx.coroutines.launch

private enum class Step { INFLATION, EVALUATION, CALENDAR, PLAN }

/**
 * Which steps a visit runs through.
 *
 * A mid-week top-up is the calendar and the two columns. The devaluation and the
 * retrospective belong to the scheduled phase and to it alone — adding a ToDo on a
 * Wednesday is not the week turning over, and must not cost 30% of the account.
 *
 * The calendar comes **before** the columns in both, which is the rule the whole
 * feature rests on: appointments first, then the week is filled around them. Fill
 * first and every appointment is a conflict; fill after and most of them are
 * simply part of the week.
 */
private fun stepsFor(midWeek: Boolean, pointsVisible: Boolean, calendar: Boolean): List<Step> =
    when {
        midWeek -> listOf(Step.CALENDAR, Step.PLAN)
        // The devaluation is booked on its weekday either way; its page is only the
        // account being shown, and goes out of sight with it.
        !pointsVisible -> Step.entries - Step.INFLATION
        else -> Step.entries
    }.filter { calendar || it != Step.CALENDAR }

/**
 * The weekly planning phase, in the three parts `Planungsphase.md` names:
 * the devaluation, the look back, and the two columns.
 */
@Composable
fun WeekPlannerScreen(
    viewModel: WeekPlannerViewModel,
    /**
     * The calendar step, over exactly the stretch this phase is laying out. Null
     * leaves the step out — the tutorial's practice week has no calendar, and
     * must not ask for the user's real Google account.
     */
    calendarViewModel: CalendarEventsViewModel?,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val inflation by viewModel.inflation.collectAsStateWithLifecycle()
    val evaluation by viewModel.evaluation.collectAsStateWithLifecycle()
    val banned by viewModel.banned.collectAsStateWithLifecycle()
    val steps = stepsFor(viewModel.isMidWeek, LocalPointsVisible.current, calendarViewModel != null)
    val pagerState = rememberPagerState { steps.size }
    ReportToTutorial(
        TutorialSignal.WEEK_STEP,
        steps[pagerState.currentPage.coerceIn(0, steps.lastIndex)].name,
    )
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }

    // The system back is a door out of the phase like any other, and the
    // devaluation is booked on the way through it.
    BackHandler { viewModel.leave(onClose) }

    EtaScreen(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(
                    start = EtaTheme.spacing.lg,
                    end = EtaTheme.spacing.lg,
                    top = EtaTheme.spacing.lg,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        EtaText(
                            text = if (viewModel.isMidWeek) "Woche ergänzen" else "Wochenplanung",
                            style = EtaTheme.typography.title,
                        )
                        EtaText(
                            text = "${state.weekStart.formatLong()} bis ${state.weekEnd.formatLong()}",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textSecondary,
                        )
                    }
                    // A phase you cannot leave is a trap; what has been pulled into
                    // the week is written as it happens, so only the unsent
                    // retrospective is lost by walking out.
                    EtaButton(
                        text = "Abbrechen",
                        style = EtaButtonStyle.Secondary,
                        onClick = { viewModel.leave(onClose) },
                    )
                }
                Spacer(Modifier.size(EtaTheme.spacing.sm))
                if (steps.size > 1) EtaProgressBar((pagerState.currentPage + 1f) / steps.size)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(EtaTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
                ) {
                    when (steps[page]) {
                        Step.INFLATION -> InflationStep(inflation, banned)
                        Step.EVALUATION -> EvaluationStep(evaluation, viewModel::setEvaluation)
                        Step.CALENDAR -> calendarViewModel?.let { CalendarEventsSection(it) }
                        Step.PLAN -> PlanStep(state, viewModel, onAdd = { adding = true })
                    }
                    Spacer(Modifier.size(EtaTheme.spacing.xl))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EtaTheme.spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
            ) {
                if (pagerState.currentPage > 0) {
                    EtaButton(
                        text = "Zurück",
                        style = EtaButtonStyle.Secondary,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (pagerState.currentPage == steps.lastIndex) {
                    EtaButton(
                        text = "Woche steht",
                        enabled = tutorialAllows(TutorialGate.WEEK_FINISH),
                        onClick = { viewModel.finish(onClose) },
                    )
                } else {
                    EtaButton(
                        text = "Weiter",
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                    )
                }
            }
        }
    }

    if (adding) {
        AddGoalDialog(
            today = viewModel.today,
            foldCandidates = state.foldable.foldCandidatesExcept(),
            onDismiss = { adding = false },
            onCreate = { name, attributes ->
                viewModel.addToCollection(name, attributes) { adding = false }
            },
        )
    }
}

/** Part 1: the devaluation, shown before it happens. */
@Composable
private fun InflationStep(preview: InflationPreview?, banned: Int?) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Inflation", style = EtaTheme.typography.heading)
            EtaText(
                text = "Jede Woche verlieren 30 % der angesparten Punkte ihren Wert. " +
                    "Punkte sind zum Ausgeben da.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            when {
                preview == null -> EtaText(
                    text = "Wird berechnet …",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textMuted,
                )

                preview.loss <= 0.0 -> EtaText(
                    text = "Nichts angespart — nichts zu verlieren.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textMuted,
                )

                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            EtaText(
                                text = "Von ${formatPoints(preview.balance)} bleiben",
                                style = EtaTheme.typography.label,
                                color = EtaTheme.colors.textSecondary,
                            )
                            EtaText(
                                text = formatPoints(preview.remaining),
                                style = EtaTheme.typography.display,
                            )
                        }
                        EtaText(
                            text = "−${formatPoints(preview.loss)}",
                            style = EtaTheme.typography.title,
                            color = EtaTheme.colors.danger,
                        )
                    }
                    EtaText(
                        text = if (preview.alreadyApplied) {
                            "Diese Woche bereits gebucht."
                        } else {
                            "Wird beim nächsten Start gebucht."
                        },
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            }
        }
    }

    if (banned != null) {
        EtaSurface(
            modifier = Modifier.fillMaxWidth(),
            borderColor = EtaTheme.colors.danger,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaText(
                    text = if (banned == 1) {
                        "Ein ToDo ist auf die Sperrliste gewandert"
                    } else {
                        "$banned ToDos sind auf die Sperrliste gewandert"
                    },
                    style = EtaTheme.typography.heading,
                    color = EtaTheme.colors.danger,
                )
                EtaText(
                    text = "Länger als einen Monat in der Sammelliste. Für ein halbes Jahr " +
                        "gesperrt und bis dahin nicht neu anlegbar.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** Part 2: the look back. */
@Composable
private fun EvaluationStep(text: String, onChange: (String) -> Unit) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Rückblick", style = EtaTheme.typography.heading)
            EtaText(
                text = WEEKLY_QUESTION,
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
            EtaTextField(
                value = text,
                onValueChange = onChange,
                placeholder = "Was war unrund?",
                singleLine = false,
            )
            EtaText(
                text = "Wird mit Datum abgelegt, wie die Tageseinträge.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
    }
}

/** Part 3: the two columns, with what is left of the week above them. */
@Composable
private fun PlanStep(state: WeekPlannerUiState, viewModel: WeekPlannerViewModel, onAdd: () -> Unit) {
    BudgetBox(state.budget)

    if (!state.canTakeOnMore) {
        EtaSurface(
            modifier = Modifier.fillMaxWidth(),
            borderColor = EtaTheme.colors.warning,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaText(
                    text = "Erst abarbeiten, dann Neues",
                    style = EtaTheme.typography.heading,
                    color = EtaTheme.colors.warning,
                )
                EtaText(
                    text = "Aus der letzten Woche liegen noch ${state.unfinished.size} " +
                        "unverplante Ziele in der Wochenliste. Die Wochenliste nimmt erst " +
                        "wieder Neues auf, wenn sie verplant oder zurückgegeben sind.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
                state.unfinished.forEach { item ->
                    EtaText(
                        text = "· ${item.name}",
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textSecondary,
                    )
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        ItemColumn(
            title = "Sammelliste",
            items = if (state.canTakeOnMore) state.collection else emptyList(),
            emptyHint = if (state.canTakeOnMore) "Leer." else "Gesperrt.",
            modifier = Modifier.weight(1f),
            onPick = viewModel::pullIntoWeek,
        )
        ItemColumn(
            title = "Diese Woche",
            items = state.weekList,
            emptyHint = "Noch nichts vorgenommen.",
            modifier = Modifier.weight(1f),
            onPick = viewModel::returnToCollection,
        )
    }

    // The Sammelliste can be empty exactly when it is needed most — a fresh setup
    // has nothing in it, and without this the whole phase has nothing to work with.
    EtaButton(
        text = "ToDo anlegen",
        style = EtaButtonStyle.Secondary,
        onClick = onAdd,
    )

    EtaText(
        text = "Tippen schiebt eine Karte in die andere Spalte. Verplant wird sie erst " +
            "in der Tagesplanung.",
        style = EtaTheme.typography.caption,
        color = EtaTheme.colors.textMuted,
    )
}

@Composable
private fun BudgetBox(budget: WeekBudget) {
    EtaSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (budget.overbooked) EtaTheme.colors.danger else EtaTheme.colors.border,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
            EtaText(
                text = "Freie Stunden",
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.textSecondary,
            )
            EtaText(
                text = if (budget.overbooked) "überbucht" else formatMinutes(budget.freeMinutes),
                style = EtaTheme.typography.display,
                color = if (budget.overbooked) {
                    EtaTheme.colors.danger
                } else {
                    EtaTheme.colors.textPrimary
                },
            )
            EtaText(
                text = "${formatMinutes(budget.committedMinutes)} stehen fest, " +
                    "${formatMinutes(budget.plannedMinutes)} sind vorgenommen — Pausen schon " +
                    "eingerechnet.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            if (budget.withoutFreeTime) {
                EtaText(
                    text = "In dieser Woche ist keine Freizeit vorgesehen.",
                    style = EtaTheme.typography.bodyStrong,
                    color = EtaTheme.colors.warning,
                )
                EtaText(
                    text = "Das darf so sein — der Verzicht wird ja vergütet. Es sollte nur " +
                        "eine Entscheidung sein und kein Versehen.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ItemColumn(
    title: String,
    items: List<Item>,
    emptyHint: String,
    modifier: Modifier = Modifier,
    onPick: (Item) -> Unit,
) {
    EtaSurface(modifier = modifier, contentPadding = EtaTheme.spacing.md) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = title, style = EtaTheme.typography.heading)
            if (items.isEmpty()) {
                EtaText(
                    text = emptyHint,
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
            items.forEach { item -> ItemCard(item = item, onClick = { onPick(item) }) }
        }
    }
}

@Composable
private fun ItemCard(item: Item, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val duration = item.estimatedDuration

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EtaTheme.colors.background, EtaTheme.shapes.small)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(EtaTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colorOf(item.category), CircleShape),
            )
            Spacer(Modifier.size(EtaTheme.spacing.sm))
            EtaText(
                text = item.name,
                style = EtaTheme.typography.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EtaText(
            text = when {
                duration == null -> "noch ohne Dauer"
                // What it really costs the week, break and all.
                else -> "${duration.formatShort()} · kostet ${costOf(duration).formatShort()}"
            },
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
    }
}
