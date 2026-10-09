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
import com.example.eta.domain.tutorial.TutorialId
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
import com.example.eta.ui.contracts.ContractsScreen
import com.example.eta.ui.growth.GrowthTasksScreen
import com.example.eta.ui.lists.SmartListsScreen
import com.example.eta.ui.root.smartListsViewModelFactory
import com.example.eta.ui.rewards.RewardsScreen
import com.example.eta.ui.root.contractsViewModelFactory
import com.example.eta.ui.root.growthTasksViewModelFactory
import com.example.eta.ui.root.rewardsViewModelFactory
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
    LaunchedEffect(Unit) {
        tutorial.enter(
            showConcept = state.requested,
            direct = TutorialId.named(state.requestedId),
        )
    }

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
            onStart = tutorial::start,
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
    val arrivedReady by tutorial.arrivedReady.collectAsStateWithLifecycle()
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
                // The points are on, as for a newcomer. The other features only
                // in a tutorial about one of them.
                LocalPointsVisible provides true,
                LocalFeatures provides tutorial.tutorial.advanced.let { on ->
                    Features(growthTasks = on, contracts = on, rewards = on)
                },
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
            arrivedReady = arrivedReady,
            onBack = tutorial::back,
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
            onCloseDay = tutorial::closeDayPressed,
        )

        TutorialStage.REEVALUATION -> ReevaluationScreen(
            viewModel = viewModel(
                key = "tutorial-reevaluation",
                factory = reevaluationViewModelFactory(container),
            ),
            onClose = { tutorial.exitPressed(TutorialStage.REEVALUATION) },
        )

        // The Extras live under every card; the evening's is the one that is
        // not inside a dialog, which would cover the coach.
        TutorialStage.CONCRETIZE, TutorialStage.EXTRAS -> ConcretizeScreen(
            viewModel = viewModel(
                key = "tutorial-concretize",
                factory = concretizeViewModelFactory(container),
            ),
            onDone = { tutorial.exitPressed(stage) },
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

        // The tab's own ways out — to a planner, to the weekly planning — are
        // shut, and it makes no offer of a tutorial from inside one.
        TutorialStage.LISTS -> SmartListsScreen(
            viewModel = viewModel(key = "tutorial-lists", factory = smartListsViewModelFactory(container)),
            onOpenPlanner = tutorial::locked,
            onPlanWeek = tutorial::locked,
            onOpenToday = { tutorial.locked() },
        )

        TutorialStage.GROWTH -> GrowthTasksScreen(
            viewModel = viewModel(key = "tutorial-growth", factory = growthTasksViewModelFactory(container)),
        )

        TutorialStage.CONTRACTS -> ContractsScreen(
            viewModel = viewModel(
                key = "tutorial-contracts",
                factory = contractsViewModelFactory(container),
            ),
        )

        TutorialStage.REWARDS -> RewardsScreen(
            viewModel = viewModel(key = "tutorial-rewards", factory = rewardsViewModelFactory(container)),
        )
    }
}

/**
 * Which tutorial.
 *
 * The Quickstart leads; the long one is drawn and greyed out rather than left
 * off, being announced. Below them the tutorials about one thing each — the
 * Extras and the three Advanced Features — which nobody is led through, and
 * which are also reachable from the settings beside what they explain.
 */
@Composable
private fun TutorialChoiceScreen(onStart: (TutorialId) -> Unit, onSkip: () -> Unit) {
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
                    EtaButton(
                        text = "Quickstart starten",
                        onClick = { onStart(TutorialId.QUICKSTART) },
                    )
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

            EtaSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                    EtaText(text = "Einzelne Themen", style = EtaTheme.typography.heading)
                    EtaText(
                        text = "Kurze Tutorials zu dem, was über den Alltag hinausgeht. Am " +
                            "besten nach dem Quickstart.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textSecondary,
                    )
                    TutorialId.further.forEach { id ->
                        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
                            EtaButton(
                                text = id.title,
                                style = EtaButtonStyle.Secondary,
                                onClick = { onStart(id) },
                            )
                            EtaText(
                                text = id.summary,
                                style = EtaTheme.typography.caption,
                                color = EtaTheme.colors.textMuted,
                            )
                        }
                    }
                }
            }

            Row {
                Spacer(Modifier.weight(1f))
                EtaButton(text = "Überspringen", style = EtaButtonStyle.Secondary, onClick = onSkip)
            }
            EtaText(
                text = "Alle Tutorials findest du jederzeit wieder: Einstellungen → Tutorial.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }
}
