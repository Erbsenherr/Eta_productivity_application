package com.example.eta.ui.setup

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.setup.SetupConflict
import com.example.eta.domain.setup.SetupPart
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaProgressBar
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.theme.EtaTheme
import kotlinx.coroutines.launch

/**
 * The steps, in order. An enum rather than a list of lambdas so the progress
 * line, the pager and the back button all count the same thing.
 *
 * **The two the app cannot run without come first** — the night and the planning
 * times — and everything after them can be skipped: [part] is set on exactly
 * those, and is what the "Überspringen" button hands to the view model.
 */
private enum class SetupStep(val title: String = "", val part: SetupPart? = null) {
    SLEEP,
    PLANNING,
    MEALS("Essen", SetupPart.MEALS),
    HOUSEKEEPING("Hausputz", SetupPart.HOUSEKEEPING),
    SPORT("Sport", SetupPart.SPORT),
    FREE_TIME("Freie Zeit", SetupPart.FREE_TIME),
    MINDFULNESS("Selbstachtsamkeit", SetupPart.MINDFULNESS),
    WORK("Arbeit und Uni", SetupPart.WORK),
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
    val skipped by viewModel.skipped.collectAsStateWithLifecycle()
    val steps = SetupStep.entries
    val pagerState = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()

    EtaScreen(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            SetupHeader(
                stepNumber = pagerState.currentPage + 1,
                stepCount = steps.size,
                modifier = Modifier.padding(
                    start = EtaTheme.spacing.lg,
                    end = EtaTheme.spacing.lg,
                    top = EtaTheme.spacing.lg,
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
                        .padding(EtaTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
                ) {
                    val step = steps[page]
                    val part = step.part
                    if (part != null && part in skipped) {
                        SkippedStep(title = step.title, onAnswer = { viewModel.unskip(part) })
                    } else {
                        when (step) {
                            // The welcome is the top of the first page, not a
                            // page of its own: the first thing shown is a question.
                            SetupStep.SLEEP -> {
                                WelcomeStep()
                                SleepStep(draft, viewModel::update)
                            }
                            SetupStep.PLANNING -> PlanningStep(draft, viewModel::update)
                            SetupStep.MEALS -> MealsStep(draft, viewModel::update)
                            SetupStep.HOUSEKEEPING -> HousekeepingStep(draft, viewModel::update)
                            SetupStep.SPORT -> SportStep(draft, viewModel::update)
                            SetupStep.FREE_TIME -> FreeTimeStep(draft, viewModel::update)
                            SetupStep.MINDFULNESS -> MindfulnessStep(draft, viewModel::update)
                            SetupStep.WORK -> WorkStep(draft, viewModel::update)
                            SetupStep.SUMMARY -> SummaryStep(outlook)
                        }
                    }

                    // The concept asks for double bookings to be flagged as they
                    // arise, so the warning travels along instead of waiting for
                    // the summary.
                    ConflictBox(conflicts = outlook.conflicts)

                    Spacer(Modifier.size(EtaTheme.spacing.xl))
                }
            }

            val currentPart = steps[pagerState.currentPage].part
            SetupFooter(
                isFirst = pagerState.currentPage == 0,
                isLast = pagerState.currentPage == steps.lastIndex,
                saving = saving,
                // Offered where a page can be left out and has not been already.
                onSkip = currentPart?.takeIf { it !in skipped }?.let { part ->
                    {
                        viewModel.skip(part)
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
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
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
    ) {
        EtaText(
            text = "Einrichtung · Schritt $stepNumber von $stepCount",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        EtaProgressBar(fraction = stepNumber.toFloat() / stepCount)
    }
}

/** Every double booking the answers so far produce, named in plain German. */
@Composable
private fun ConflictBox(conflicts: List<SetupConflict>, modifier: Modifier = Modifier) {
    if (conflicts.isEmpty()) return

    EtaSurface(
        modifier = modifier.fillMaxWidth(),
        borderColor = EtaTheme.colors.warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = if (conflicts.size == 1) {
                    "Eine Doppeltbelegung"
                } else {
                    "${conflicts.size} Doppeltbelegungen"
                },
                style = EtaTheme.typography.heading,
                color = EtaTheme.colors.warning,
            )
            conflicts.take(MAX_SHOWN_CONFLICTS).forEach { conflict ->
                EtaText(
                    text = "${conflict.weekday.formatLong()}: " +
                        "${conflict.first.label} und ${conflict.second.label} " +
                        "überschneiden sich um ${conflict.toMinute - conflict.fromMinute} min.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
            }
            if (conflicts.size > MAX_SHOWN_CONFLICTS) {
                EtaText(
                    text = "… und ${conflicts.size - MAX_SHOWN_CONFLICTS} weitere.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
            EtaText(
                text = "Du kannst trotzdem fortfahren — der Tagesplaner zeigt dir die " +
                    "Überschneidung dann erneut.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
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
    onSkip: (() -> Unit)?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(EtaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
    ) {
    // On a line of its own: three buttons in one row do not fit a phone, and the
    // last one is then laid out at no width at all.
    if (onSkip != null) {
        EtaButton(
            text = "Überspringen",
            style = EtaButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            onClick = onSkip,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
    ) {
        if (!isFirst) {
            EtaButton(
                text = "Zurück",
                style = EtaButtonStyle.Secondary,
                onClick = onBack,
            )
        }
        Spacer(Modifier.weight(1f))
        if (isLast) {
            EtaButton(
                text = if (saving) "Wird angelegt …" else "Fertig",
                enabled = !saving,
                onClick = onFinish,
            )
        } else {
            EtaButton(text = "Weiter", onClick = onNext)
        }
    }
    }
}

/** A page the user left out, with the way back in. */
@Composable
private fun SkippedStep(title: String, onAnswer: () -> Unit) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = title, style = EtaTheme.typography.title)
            EtaText(
                text = "Übersprungen — dafür wird nichts angelegt. Du kannst es später " +
                    "jederzeit im Reiter Listen als wiederkehrende Aufgabe nachholen.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
            EtaButton(
                text = "Doch beantworten",
                style = EtaButtonStyle.Secondary,
                onClick = onAnswer,
            )
        }
    }
}
