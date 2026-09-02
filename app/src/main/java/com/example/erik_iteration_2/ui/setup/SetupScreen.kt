package com.example.erik_iteration_2.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erik_iteration_2.domain.setup.SetupConflict
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlinx.coroutines.launch

/**
 * The steps, in order. An enum rather than a list of lambdas so the progress
 * line, the pager and the back button all count the same thing.
 */
private enum class SetupStep {
    WELCOME,
    SLEEP,
    MEALS,
    HOUSEKEEPING,
    SPORT,
    FREE_TIME,
    MINDFULNESS,
    WORK,
    PLANNING,
    SUMMARY,
}

/**
 * The questionnaire that runs before the app is usable.
 *
 * It swipes rather than navigates, in the spirit of the rest of the app, and the
 * buttons move the same pager — so a user who prefers tapping and one who prefers
 * flicking end up in the same place.
 */
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    modifier: Modifier = Modifier,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val outlook by viewModel.outlook.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val steps = SetupStep.entries
    val pagerState = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()

    ErikScreen(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            SetupHeader(
                stepNumber = pagerState.currentPage + 1,
                stepCount = steps.size,
                modifier = Modifier.padding(
                    start = ErikTheme.spacing.lg,
                    end = ErikTheme.spacing.lg,
                    top = ErikTheme.spacing.lg,
                ),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(ErikTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
                ) {
                    when (steps[page]) {
                        SetupStep.WELCOME -> WelcomeStep()
                        SetupStep.SLEEP -> SleepStep(draft, viewModel::update)
                        SetupStep.MEALS -> MealsStep(draft, viewModel::update)
                        SetupStep.HOUSEKEEPING -> HousekeepingStep(draft, viewModel::update)
                        SetupStep.SPORT -> SportStep(draft, viewModel::update)
                        SetupStep.FREE_TIME -> FreeTimeStep(draft, viewModel::update)
                        SetupStep.MINDFULNESS -> MindfulnessStep(draft, viewModel::update)
                        SetupStep.WORK -> WorkStep(draft, viewModel::update)
                        SetupStep.PLANNING -> PlanningStep(draft, viewModel::update)
                        SetupStep.SUMMARY -> SummaryStep(outlook)
                    }

                    // The concept asks for double bookings to be flagged as they
                    // arise, so the warning travels along instead of waiting for
                    // the summary.
                    ConflictBox(conflicts = outlook.conflicts)

                    Spacer(Modifier.size(ErikTheme.spacing.xl))
                }
            }

            SetupFooter(
                isFirst = pagerState.currentPage == 0,
                isLast = pagerState.currentPage == steps.lastIndex,
                saving = saving,
                onBack = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                },
                onNext = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                onFinish = viewModel::finish,
            )
        }
    }
}

@Composable
private fun SetupHeader(stepNumber: Int, stepCount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm),
    ) {
        ErikText(
            text = "Einrichtung · Schritt $stepNumber von $stepCount",
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.textMuted,
        )
        ProgressBar(fraction = stepNumber.toFloat() / stepCount)
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
                .fillMaxHeight()
                .background(ErikTheme.colors.accent, ErikTheme.shapes.pill),
        )
    }
}

/** Every double booking the answers so far produce, named in plain German. */
@Composable
private fun ConflictBox(conflicts: List<SetupConflict>, modifier: Modifier = Modifier) {
    if (conflicts.isEmpty()) return

    ErikSurface(
        modifier = modifier.fillMaxWidth(),
        borderColor = ErikTheme.colors.warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikText(
                text = if (conflicts.size == 1) {
                    "Eine Doppeltbelegung"
                } else {
                    "${conflicts.size} Doppeltbelegungen"
                },
                style = ErikTheme.typography.heading,
                color = ErikTheme.colors.warning,
            )
            conflicts.take(MAX_SHOWN_CONFLICTS).forEach { conflict ->
                ErikText(
                    text = "${conflict.weekday.formatLong()}: " +
                        "${conflict.first.label} und ${conflict.second.label} " +
                        "überschneiden sich um ${conflict.toMinute - conflict.fromMinute} min.",
                    style = ErikTheme.typography.body,
                    color = ErikTheme.colors.textSecondary,
                )
            }
            if (conflicts.size > MAX_SHOWN_CONFLICTS) {
                ErikText(
                    text = "… und ${conflicts.size - MAX_SHOWN_CONFLICTS} weitere.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                )
            }
            ErikText(
                text = "Du kannst trotzdem fortfahren — der Tagesplaner zeigt dir die " +
                    "Überschneidung dann erneut.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
        }
    }
}

private const val MAX_SHOWN_CONFLICTS = 6

@Composable
private fun SetupFooter(
    isFirst: Boolean,
    isLast: Boolean,
    saving: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ErikTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md),
    ) {
        if (!isFirst) {
            ErikButton(
                text = "Zurück",
                style = ErikButtonStyle.Secondary,
                onClick = onBack,
            )
        }
        Spacer(Modifier.weight(1f))
        if (isLast) {
            ErikButton(
                text = if (saving) "Wird angelegt …" else "Fertig",
                enabled = !saving,
                onClick = onFinish,
            )
        } else {
            ErikButton(text = "Weiter", onClick = onNext)
        }
    }
}
