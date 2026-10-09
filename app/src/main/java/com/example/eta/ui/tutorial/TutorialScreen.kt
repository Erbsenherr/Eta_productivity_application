package com.example.eta.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.eta.data.repository.WeekScope
import com.example.eta.domain.tutorial.TutorialStage
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.Features
import com.example.eta.ui.components.LocalEtaClock
import com.example.eta.ui.components.LocalFeatures
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.components.LocalTutorialGuide
import com.example.eta.ui.concretize.ConcretizeScreen
import com.example.eta.ui.dashboard.DashboardScreen
import com.example.eta.ui.planner.DayPlannerScreen
import com.example.eta.ui.planner.PlannerDay
import com.example.eta.ui.quickadd.quickAddViewModelFactory
import com.example.eta.ui.reevaluation.ReevaluationScreen
import com.example.eta.ui.root.concretizeViewModelFactory
import com.example.eta.ui.root.dashboardViewModelFactory
import com.example.eta.ui.root.plannerViewModelFactory
import com.example.eta.ui.root.reevaluationViewModelFactory
import com.example.eta.ui.root.weekPlannerViewModelFactory
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.weekplanner.WeekPlannerScreen

/**
 * The tutorial, from its choice of length to its last step.
 *
 * **The screens are the app's own**, mounted over a [TutorialSession] — a
 * practice database, a clock, a view model store — with [TutorialCoach] beneath
 * them saying what to do. Nothing is rebuilt for the tutorial, so nothing in it
 * can drift from the app it explains.
 *
 * **There is no way sideways.** No tab bar is drawn, the system back is taken,
 * and every button a screen has for leaving — "Heute umplanen", "Woche steht",
 * "Zurück" — arrives here instead of at the navigation: it either is the step
 * being asked for, or it says that this is locked for now.
 */
@Composable
fun TutorialHost(store: TutorialStore) {
    val context = LocalContext.current
    val tutorial: TutorialViewModel = viewModel(factory = tutorialViewModelFactory(context, store))
    val state by store.state.collectAsStateWithLifecycle()
    val phase by tutorial.phase.collectAsStateWithLifecycle()
    val session by tutorial.session.collectAsStateWithLifecycle()

    // Asked for from the settings, the idea of the app is told again first; on
    // a first run it was read a moment ago, before the questionnaire.
    LaunchedEffect(Unit) { tutorial.enter(showConcept = state.requested) }

    // Before the screens, so that a phase's own back handler still comes first.
    // Only while it runs: on the pages before it, back leaves the app as ever.
    BackHandler(enabled = phase == TutorialPhase.RUNNING) { tutorial.locked() }

    val running = session
    when {
        phase == TutorialPhase.CONCEPT -> TutorialIntroScreen(
            action = "Weiter",
            onStart = tutorial::conceptRead,
        )

        phase == TutorialPhase.CHOICE -> TutorialChoiceScreen(
            onQuickstart = tutorial::startQuickstart,
            onSkip = tutorial::finish,
        )

        phase == TutorialPhase.RUNNING && running != null -> RunningTutorial(tutorial, running)

        // Idle for a frame on the way in or out, or laying the example day down.
        else -> EtaScreen {}
    }
}

@Composable
private fun RunningTutorial(tutorial: TutorialViewModel, session: TutorialSession) {
    val index by tutorial.index.collectAsStateWithLifecycle()
    val facts by tutorial.facts.collectAsStateWithLifecycle()
    val hint by tutorial.hint.collectAsStateWithLifecycle()
    val step = tutorial.steps[index.coerceIn(0, tutorial.steps.lastIndex)]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EtaTheme.colors.background),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // The coach sits over the navigation bar, as the tab bar does
                // elsewhere; a screen insetting for it too would leave a gap.
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            CompositionLocalProvider(
                // Everything the screens build, they build in the session.
                LocalViewModelStoreOwner provides session,
                LocalTutorialGuide provides tutorial.guide,
                LocalEtaClock provides session.clock,
                // What a newcomer's own app shows: no points, no extra tabs.
                LocalPointsVisible provides false,
                LocalFeatures provides Features(growthTasks = false, contracts = false, rewards = false),
            ) {
                StageScreen(step.stage, session, tutorial)
            }
        }

        TutorialCoach(
            step = step,
            number = index + 1,
            total = tutorial.steps.size,
            facts = facts,
            hint = hint,
            onNext = tutorial::next,
            onQuit = tutorial::finish,
        )
    }
}

/** The app's own screen for [stage], wired to the practice container. */
@Composable
private fun StageScreen(
    stage: TutorialStage,
    session: TutorialSession,
    tutorial: TutorialViewModel,
) {
    val container = session.container

    when (stage) {
        TutorialStage.DASHBOARD -> DashboardScreen(
            viewModel = viewModel(key = "tutorial-dashboard", factory = dashboardViewModelFactory(container)),
            quickAddViewModel = viewModel(
                key = "tutorial-quickadd",
                factory = quickAddViewModelFactory(container),
            ),
            onPlanTomorrow = tutorial::locked,
            onReplanToday = tutorial::locked,
            onPlanWeek = tutorial::locked,
            onCloseDay = tutorial::locked,
        )

        TutorialStage.REEVALUATION -> ReevaluationScreen(
            viewModel = viewModel(
                key = "tutorial-reevaluation",
                factory = reevaluationViewModelFactory(container),
            ),
            onClose = { tutorial.exitPressed(TutorialStage.REEVALUATION) },
        )

        TutorialStage.CONCRETIZE -> ConcretizeScreen(
            viewModel = viewModel(
                key = "tutorial-concretize",
                factory = concretizeViewModelFactory(container),
            ),
            onDone = { tutorial.exitPressed(TutorialStage.CONCRETIZE) },
        )

        TutorialStage.WEEK -> WeekPlannerScreen(
            viewModel = viewModel(
                key = "tutorial-week",
                factory = weekPlannerViewModelFactory(container, WeekScope.SCHEDULED),
            ),
            // No calendar in a practice week — and none of the user's asked for.
            calendarViewModel = null,
            onClose = { tutorial.exitPressed(TutorialStage.WEEK) },
        )

        TutorialStage.PLANNER -> DayPlannerScreen(
            viewModel = viewModel(
                key = "tutorial-planner",
                factory = plannerViewModelFactory(container, PlannerDay.TOMORROW),
            ),
            onClose = { tutorial.exitPressed(TutorialStage.PLANNER) },
            onTopUpWeek = tutorial::locked,
        )
    }
}

/**
 * Quickstart, or the long one.
 *
 * The long one is drawn and greyed out rather than left off: it is announced,
 * and a choice of one would not read as a choice.
 */
@Composable
private fun TutorialChoiceScreen(onQuickstart: () -> Unit, onSkip: () -> Unit) {
    EtaScreen {
        TutorialPage {
            EtaText(text = "Tutorial", style = EtaTheme.typography.display)
            EtaText(
                text = "Eta führt dich einmal durch einen simulierten Tag: vom Dashboard über " +
                    "den Tagesabschluss bis zum Plan für morgen.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )

            EtaSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                    EtaText(text = "Quickstart", style = EtaTheme.typography.heading)
                    EtaText(
                        text = "Etwa fünf Minuten. Du probierst alles selbst aus — mit " +
                            "Übungsdaten. Deine eigenen Aufgaben und Einstellungen bleiben " +
                            "unberührt.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textSecondary,
                    )
                    EtaButton(text = "Quickstart starten", onClick = onQuickstart)
                }
            }

            EtaSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                    EtaText(
                        text = "Ausführliches Tutorial",
                        style = EtaTheme.typography.heading,
                        color = EtaTheme.colors.textMuted,
                    )
                    EtaText(
                        text = "Folgt in einem späteren Update.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                    EtaButton(text = "Noch nicht verfügbar", enabled = false, onClick = {})
                }
            }

            Row {
                Spacer(Modifier.weight(1f))
                EtaButton(text = "Überspringen", style = EtaButtonStyle.Secondary, onClick = onSkip)
            }
            EtaText(
                text = "Du kannst das Tutorial jederzeit wiederholen: in den Einstellungen, " +
                    "ganz unten.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }
}
