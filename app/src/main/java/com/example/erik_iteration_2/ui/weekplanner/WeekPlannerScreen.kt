package com.example.erik_iteration_2.ui.weekplanner

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
import androidx.compose.foundation.layout.height
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
import com.example.erik_iteration_2.data.repository.InflationPreview
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.planning.WeekBudget
import com.example.erik_iteration_2.domain.planning.costOf
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.format.formatMinutes
import com.example.erik_iteration_2.ui.format.formatPoints
import com.example.erik_iteration_2.ui.format.formatShort
import com.example.erik_iteration_2.ui.theme.ErikTheme
import com.example.erik_iteration_2.ui.theme.colorOf
import kotlinx.coroutines.launch

private enum class Step { INFLATION, EVALUATION, PLAN }

/**
 * Which steps a visit runs through.
 *
 * A mid-week top-up is only the two columns. The devaluation and the retrospective
 * belong to the scheduled phase and to it alone — adding a ToDo on a Wednesday is
 * not the week turning over, and must not cost 30% of the account.
 */
private fun stepsFor(midWeek: Boolean): List<Step> =
    if (midWeek) listOf(Step.PLAN) else Step.entries

/**
 * The weekly planning phase, in the three parts `Planungsphase.md` names:
 * the devaluation, the look back, and the two columns.
 */
@Composable
fun WeekPlannerScreen(
    viewModel: WeekPlannerViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val inflation by viewModel.inflation.collectAsStateWithLifecycle()
    val evaluation by viewModel.evaluation.collectAsStateWithLifecycle()
    val banned by viewModel.banned.collectAsStateWithLifecycle()
    val steps = stepsFor(viewModel.isMidWeek)
    val pagerState = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }

    // The system back is a door out of the phase like any other, and the
    // devaluation is booked on the way through it.
    BackHandler { viewModel.leave(onClose) }

    ErikScreen(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(
                    start = ErikTheme.spacing.lg,
                    end = ErikTheme.spacing.lg,
                    top = ErikTheme.spacing.lg,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        ErikText(
                            text = if (viewModel.isMidWeek) "Woche ergänzen" else "Wochenplanung",
                            style = ErikTheme.typography.title,
                        )
                        ErikText(
                            text = "${state.weekStart.formatLong()} bis ${state.weekEnd.formatLong()}",
                            style = ErikTheme.typography.caption,
                            color = ErikTheme.colors.textSecondary,
                        )
                    }
                    // A phase you cannot leave is a trap; what has been pulled into
                    // the week is written as it happens, so only the unsent
                    // retrospective is lost by walking out.
                    ErikButton(
                        text = "Abbrechen",
                        style = ErikButtonStyle.Secondary,
                        onClick = { viewModel.leave(onClose) },
                    )
                }
                Spacer(Modifier.size(ErikTheme.spacing.sm))
                if (steps.size > 1) ProgressBar((pagerState.currentPage + 1f) / steps.size)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(ErikTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
                ) {
                    when (steps[page]) {
                        Step.INFLATION -> InflationStep(inflation, banned)
                        Step.EVALUATION -> EvaluationStep(evaluation, viewModel::setEvaluation)
                        Step.PLAN -> PlanStep(state, viewModel, onAdd = { adding = true })
                    }
                    Spacer(Modifier.size(ErikTheme.spacing.xl))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ErikTheme.spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm),
            ) {
                if (pagerState.currentPage > 0) {
                    ErikButton(
                        text = "Zurück",
                        style = ErikButtonStyle.Secondary,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (pagerState.currentPage == steps.lastIndex) {
                    ErikButton(text = "Woche steht", onClick = { viewModel.finish(onClose) })
                } else {
                    ErikButton(
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
            onDismiss = { adding = false },
            onCreate = { name, category, priority, inDays, duration ->
                viewModel.addToCollection(name, category, priority, inDays, duration) {
                    adding = false
                }
            },
        )
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(ErikTheme.colors.border, ErikTheme.shapes.pill),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(ErikTheme.colors.accent, ErikTheme.shapes.pill),
        )
    }
}

/** Part 1: the devaluation, shown before it happens. */
@Composable
private fun InflationStep(preview: InflationPreview?, banned: Int?) {
    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            ErikText(text = "Inflation", style = ErikTheme.typography.heading)
            ErikText(
                text = "Jede Woche verlieren 30 % der angesparten Punkte ihren Wert. " +
                    "Punkte sind zum Ausgeben da.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )

            when {
                preview == null -> ErikText(
                    text = "Wird berechnet …",
                    style = ErikTheme.typography.body,
                    color = ErikTheme.colors.textMuted,
                )

                preview.loss <= 0.0 -> ErikText(
                    text = "Nichts angespart — nichts zu verlieren.",
                    style = ErikTheme.typography.body,
                    color = ErikTheme.colors.textMuted,
                )

                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            ErikText(
                                text = "Von ${formatPoints(preview.balance)} bleiben",
                                style = ErikTheme.typography.label,
                                color = ErikTheme.colors.textSecondary,
                            )
                            ErikText(
                                text = formatPoints(preview.remaining),
                                style = ErikTheme.typography.display,
                            )
                        }
                        ErikText(
                            text = "−${formatPoints(preview.loss)}",
                            style = ErikTheme.typography.title,
                            color = ErikTheme.colors.danger,
                        )
                    }
                    ErikText(
                        text = if (preview.alreadyApplied) {
                            "Diese Woche bereits gebucht."
                        } else {
                            "Wird beim nächsten Start gebucht."
                        },
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.textMuted,
                    )
                }
            }
        }
    }

    if (banned != null) {
        ErikSurface(
            modifier = Modifier.fillMaxWidth(),
            borderColor = ErikTheme.colors.danger,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                ErikText(
                    text = if (banned == 1) {
                        "Ein ToDo ist auf die Sperrliste gewandert"
                    } else {
                        "$banned ToDos sind auf die Sperrliste gewandert"
                    },
                    style = ErikTheme.typography.heading,
                    color = ErikTheme.colors.danger,
                )
                ErikText(
                    text = "Länger als einen Monat in der Sammelliste. Für ein halbes Jahr " +
                        "gesperrt und bis dahin nicht neu anlegbar.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** Part 2: the look back. */
@Composable
private fun EvaluationStep(text: String, onChange: (String) -> Unit) {
    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            ErikText(text = "Rückblick", style = ErikTheme.typography.heading)
            ErikText(
                text = WEEKLY_QUESTION,
                style = ErikTheme.typography.body,
                color = ErikTheme.colors.textSecondary,
            )
            ErikTextField(
                value = text,
                onValueChange = onChange,
                placeholder = "Was war unrund?",
                singleLine = false,
            )
            ErikText(
                text = "Wird mit Datum abgelegt, wie die Tageseinträge.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
        }
    }
}

/** Part 3: the two columns, with what is left of the week above them. */
@Composable
private fun PlanStep(state: WeekPlannerUiState, viewModel: WeekPlannerViewModel, onAdd: () -> Unit) {
    BudgetBox(state.budget)

    if (!state.canTakeOnMore) {
        ErikSurface(
            modifier = Modifier.fillMaxWidth(),
            borderColor = ErikTheme.colors.warning,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                ErikText(
                    text = "Erst abarbeiten, dann Neues",
                    style = ErikTheme.typography.heading,
                    color = ErikTheme.colors.warning,
                )
                ErikText(
                    text = "Aus der letzten Woche stehen noch  Ziele " +
                        "offen. Die Wochenliste nimmt erst wieder Neues auf, wenn sie " +
                        "abgearbeitet sind — zurückgeben geht jederzeit.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textSecondary,
                )
                state.unfinished.forEach { item ->
                    ErikText(
                        text = "· ",
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.textSecondary,
                    )
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
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
    ErikButton(
        text = "ToDo anlegen",
        style = ErikButtonStyle.Secondary,
        onClick = onAdd,
    )

    ErikText(
        text = "Tippen schiebt eine Karte in die andere Spalte. Verplant wird sie erst " +
            "in der Tagesplanung.",
        style = ErikTheme.typography.caption,
        color = ErikTheme.colors.textMuted,
    )
}

@Composable
private fun BudgetBox(budget: WeekBudget) {
    ErikSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (budget.overbooked) ErikTheme.colors.danger else ErikTheme.colors.border,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.xs)) {
            ErikText(
                text = "Freie Stunden",
                style = ErikTheme.typography.label,
                color = ErikTheme.colors.textSecondary,
            )
            ErikText(
                text = if (budget.overbooked) "überbucht" else formatMinutes(budget.freeMinutes),
                style = ErikTheme.typography.display,
                color = if (budget.overbooked) {
                    ErikTheme.colors.danger
                } else {
                    ErikTheme.colors.textPrimary
                },
            )
            ErikText(
                text = "${formatMinutes(budget.committedMinutes)} stehen fest, " +
                    "${formatMinutes(budget.plannedMinutes)} sind vorgenommen — Pausen schon " +
                    "eingerechnet.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )

            if (budget.withoutFreeTime) {
                ErikText(
                    text = "In dieser Woche ist keine Freizeit vorgesehen.",
                    style = ErikTheme.typography.bodyStrong,
                    color = ErikTheme.colors.warning,
                )
                ErikText(
                    text = "Das darf so sein — der Verzicht wird ja vergütet. Es sollte nur " +
                        "eine Entscheidung sein und kein Versehen.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
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
    ErikSurface(modifier = modifier, contentPadding = ErikTheme.spacing.md) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            ErikText(text = title, style = ErikTheme.typography.heading)
            if (items.isEmpty()) {
                ErikText(
                    text = emptyHint,
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
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
            .background(ErikTheme.colors.background, ErikTheme.shapes.small)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(ErikTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colorOf(item.category), CircleShape),
            )
            Spacer(Modifier.size(ErikTheme.spacing.sm))
            ErikText(
                text = item.name,
                style = ErikTheme.typography.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ErikText(
            text = when {
                duration == null -> "noch ohne Dauer"
                // What it really costs the week, break and all.
                else -> "${duration.formatShort()} · kostet ${costOf(duration).formatShort()}"
            },
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.textMuted,
        )
    }
}
