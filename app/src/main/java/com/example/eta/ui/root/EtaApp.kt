package com.example.eta.ui.root

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.week.WeekView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eta.data.repository.WeekScope
import com.example.eta.di.AppContainer
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import com.example.eta.domain.planning.PlanningPhase
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaIcon
import com.example.eta.ui.components.EtaTabBar
import com.example.eta.ui.calendar.CalendarEventsViewModel
import com.example.eta.ui.calendar.CalendarStepScreen
import com.example.eta.ui.components.EtaTabItem
import com.example.eta.ui.concretize.ConcretizeScreen
import com.example.eta.ui.concretize.ConcretizeViewModel
import com.example.eta.ui.contracts.ContractsScreen
import com.example.eta.ui.contracts.ContractsViewModel
import com.example.eta.ui.dashboard.DashboardScreen
import com.example.eta.ui.dashboard.DashboardViewModel
import com.example.eta.ui.growth.GrowthTasksScreen
import com.example.eta.ui.growth.GrowthTasksViewModel
import com.example.eta.ui.lists.SmartListsScreen
import com.example.eta.ui.lists.SmartListsViewModel
import com.example.eta.ui.planner.DayPlannerScreen
import com.example.eta.ui.planner.DayPlannerViewModel
import com.example.eta.ui.planner.PlannerDay
import com.example.eta.ui.quickadd.quickAddViewModelFactory
import com.example.eta.ui.reevaluation.ReevaluationScreen
import com.example.eta.ui.reevaluation.ReevaluationViewModel
import com.example.eta.ui.reminders.RemindersScreen
import com.example.eta.ui.reminders.RemindersViewModel
import com.example.eta.ui.notes.NotesScreen
import com.example.eta.ui.notes.NotesViewModel
import com.example.eta.ui.rewards.RewardsScreen
import com.example.eta.ui.stats.StatisticsScreen
import com.example.eta.ui.stats.StatisticsViewModel
import com.example.eta.ui.rewards.RewardsViewModel
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.components.Features
import com.example.eta.ui.components.LocalFeatures
import androidx.compose.runtime.CompositionLocalProvider
import com.example.eta.ui.settings.CalendarSettingsViewModel
import com.example.eta.ui.settings.SettingsScreen
import com.example.eta.ui.settings.SettingsViewModel
import com.example.eta.ui.setup.SetupScreen
import com.example.eta.ui.setup.SetupWeekReview
import com.example.eta.ui.setup.SetupViewModel
import com.example.eta.domain.tutorial.TutorialId
import com.example.eta.ui.tutorial.TutorialHost
import com.example.eta.ui.tutorial.TutorialIntroScreen
import com.example.eta.ui.vacation.VacationScreen
import com.example.eta.ui.vacation.VacationViewModel
import com.example.eta.ui.week.WeekTabScreen
import com.example.eta.ui.week.WeekTabViewModel
import com.example.eta.ui.weekplanner.WeekPlannerScreen
import com.example.eta.ui.weekplanner.WeekPlannerViewModel
import com.example.eta.widget.QuickAddWidgetProvider
import kotlinx.datetime.todayIn

/**
 * Picks the first screen: the questionnaire until it has been answered, the
 * dashboard afterwards. Finishing the setup flips this by itself, since the
 * destination is derived from the stored answers.
 */
@Composable
fun EtaApp(
    container: AppContainer,
    deferPhase: PlanningPhase? = null,
    onDeferHandled: () -> Unit = {},
    openPhase: PlanningPhase? = null,
    onOpenHandled: () -> Unit = {},
) {
    val rootViewModel: RootViewModel = viewModel(factory = rootViewModelFactory(container))
    val destination by rootViewModel.destination.collectAsStateWithLifecycle()

    val tutorial by container.tutorialStore.state.collectAsStateWithLifecycle()

    NotificationPermission()

    deferPhase?.let { phase ->
        DeferDialog(
            phase = phase,
            onDismiss = onDeferHandled,
            onDefer = { hours ->
                container.planningAlarmCoordinator.defer(phase, hours)
                onDeferHandled()
            },
        )
    }

    // The same instance the questionnaire fills in — it lives in the activity's
    // store — asked here whether its last screen is still owed.
    val setupViewModel: SetupViewModel = viewModel(factory = setupViewModelFactory(container))
    val weekReview by setupViewModel.weekReview.collectAsStateWithLifecycle()

    when (destination) {
        RootDestination.Loading -> EtaScreen {}

        // The idea of the app comes before the first question about it. Only on
        // a first run: once read, a second pass goes straight to the questions.
        RootDestination.Setup -> if (!tutorial.introSeen) {
            TutorialIntroScreen(
                onStart = container.tutorialStore::introDone,
                firstRun = true,
            )
        } else {
            SetupScreen(
                viewModel = viewModel(factory = setupViewModelFactory(container)),
                calendarViewModel = viewModel(factory = calendarSettingsViewModelFactory(container)),
            )
        }

        // The questionnaire's last screen, before anything else the app owes:
        // the week it has just laid down, to be looked over and pulled straight.
        RootDestination.Dashboard -> if (weekReview) {
            SetupWeekReview(onDone = setupViewModel::endWeekReview) { footer ->
                WeekTabScreen(
                    viewModel = viewModel(key = "setup-week", factory = weekTabViewModelFactory(container)),
                    onlyView = WeekView.GENERAL,
                    showAppointments = true,
                    footer = footer,
                )
            }
        } else if (tutorial.active) {
            // Owed after a first setup, or asked for from the settings. In place
            // of the scaffold rather than over it: no tab bar, no flow, nothing
            // to wander off into — and nothing of the user's on screen meanwhile.
            TutorialHost(container.tutorialStore)
        } else {
            // Handed down rather than read by each screen: the switch hides
            // figures in a dozen unrelated places. True until the setup arrives,
            // which is what it was before there was a switch.
            val setup by container.setupRepository.observe()
                .collectAsStateWithLifecycle(initialValue = null)
            val stored = setup
            if (stored == null) {
                // A frame or two until the row arrives. Drawing the tabs before
                // it would flash the ones that are switched off.
                EtaScreen {}
            } else {
                CompositionLocalProvider(
                    LocalPointsVisible provides stored.pointsSystem,
                    LocalFeatures provides Features(
                        growthTasks = stored.growthTasks,
                        contracts = stored.contracts,
                        rewards = stored.rewards,
                        statistics = stored.statistics,
                        notes = stored.notes,
                    ),
                ) {
                    MainScaffold(
                        container = container,
                        rootViewModel = rootViewModel,
                        openPhase = openPhase,
                        onOpenHandled = onOpenHandled,
                    )
                }
            }
        }
    }
}

/**
 * The four tabs, and the flows that take over the screen.
 *
 * The split is the point: a **tab** is a place you can always get back to, so the
 * bar stays put; a **flow** — a planning phase, the holiday editor — is something
 * you are in the middle of, so it covers the bar and leaves by finishing or by
 * going back. Mixing the two is what made the old flat enum awkward: the
 * reevaluation and the dashboard were peers, and they are not.
 */
@Composable
private fun MainScaffold(
    container: AppContainer,
    rootViewModel: RootViewModel,
    openPhase: PlanningPhase? = null,
    onOpenHandled: () -> Unit = {},
) {
    // Saveable, not merely remembered: a rotation is a configuration change, and
    // losing these two to one used to throw the user out of a planning phase and
    // back onto the dashboard. Saved by name, so what goes into the bundle is a
    // plain String rather than anything that needs a parcelling story.
    val context = LocalContext.current
    val design by container.designStore.choice.collectAsStateWithLifecycle()
    val tutorialState by container.tutorialStore.state.collectAsStateWithLifecycle()

    var savedTab by rememberSaveable(stateSaver = TAB_SAVER) { mutableStateOf(EtaTab.Today) }
    // What each tab had on screen — how far it was scrolled, which page of it
    // was open — kept while another tab or a flow is in front. Without it a
    // screen that leaves the composition comes back at its top: `rememberSaveable`
    // survives a rotation by itself, but not being taken off the screen.
    val tabState = rememberSaveableStateHolder()
    // The tabs of the features that are switched on. One that was open when its
    // feature went — or was saved before a restart — falls back to the first.
    val features = LocalFeatures.current
    val tabs = EtaTab.entries.filter { it.isShown(features) }
    val tab = savedTab.takeIf { it in tabs } ?: EtaTab.Today
    var flow by rememberSaveable(stateSaver = FLOW_SAVER) { mutableStateOf<AppFlow?>(null) }
    // Where today's planner opens, when it was asked for from an entry of the
    // Tagesliste. Cleared with the flow, so "Heute umplanen" opens as it always did.
    var plannerFocus by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(flow) {
        if (flow != AppFlow.PlannerToday) plannerFocus = null
    }

    // Answering the alarm lands in the phase itself. Dropping the user on the
    // dashboard would make them do the navigating they were interrupted for.
    LaunchedEffect(openPhase) {
        when (openPhase) {
            PlanningPhase.DAILY -> flow = AppFlow.Reevaluation
            PlanningPhase.WEEKLY -> flow = AppFlow.WeekPlanner
            null -> Unit
        }
        if (openPhase != null) onOpenHandled()
    }

    // View models capture "today" when they are built. Keying them to the date
    // rebuilds them when it rolls over, so an app left open overnight does not
    // spend the morning showing yesterday.
    val dateKey = rememberDateKey()

    // Back leaves a flow first, then returns to the first tab.
    BackHandler(enabled = flow != null) { flow = null }
    BackHandler(enabled = flow == null && tab != EtaTab.Today) { savedTab = EtaTab.Today }

    // Covers both arrivals: the first composition after the questionnaire — which
    // is the earliest the alarm can be laid down, since the setup holds its times
    // — and every return from a phase, which is when its alarm can stand down.
    // Two effects used to do this, and both fired at once on landing here.
    LaunchedEffect(flow) {
        if (flow == null) rootViewModel.rescheduleAlarms()
    }

    if (flow != null) {
        when (flow) {
            AppFlow.Planner -> DayPlannerScreen(
                viewModel = viewModel(
                    key = "planner-$dateKey",
                    factory = plannerViewModelFactory(container, PlannerDay.TOMORROW),
                ),
                onClose = { flow = null },
                onTopUpWeek = { flow = AppFlow.WeekTopUp },
            )

            // The same screen on the day already running. A flow rather than a
            // tab: it is something you are in the middle of, and it leaves by
            // being done with.
            AppFlow.PlannerToday -> DayPlannerScreen(
                viewModel = viewModel(
                    key = "planner-today-$dateKey",
                    factory = plannerViewModelFactory(container, PlannerDay.TODAY),
                ),
                onClose = { flow = null },
                onTopUpWeek = { flow = AppFlow.WeekTopUp },
                focusMinute = plannerFocus,
            )

            AppFlow.WeekPlanner -> {
                val weekViewModel: WeekPlannerViewModel = viewModel(
                    key = "week-$dateKey",
                    factory = weekPlannerViewModelFactory(container, WeekScope.SCHEDULED),
                )
                WeekPlannerScreen(
                    viewModel = weekViewModel,
                    calendarViewModel = viewModel(
                        key = "calendar-week-$dateKey",
                        factory = calendarViewModelFactory(
                            container = container,
                            from = weekViewModel.weekStart,
                            to = weekViewModel.weekEnd,
                        ),
                    ),
                    onClose = { flow = null },
                    weekAdjust = { onBack -> WeekAdjustScreen(container, weekViewModel.weekStart, dateKey, onBack) },
                )
            }

            // Same screen, different stretch: adding to the week already running.
            AppFlow.WeekTopUp -> {
                val weekViewModel: WeekPlannerViewModel = viewModel(
                    key = "week-topup-$dateKey",
                    factory = weekPlannerViewModelFactory(container, WeekScope.MIDWEEK),
                )
                WeekPlannerScreen(
                    viewModel = weekViewModel,
                    calendarViewModel = viewModel(
                        key = "calendar-topup-$dateKey",
                        factory = calendarViewModelFactory(
                            container = container,
                            from = weekViewModel.weekStart,
                            to = weekViewModel.weekEnd,
                        ),
                    ),
                    onClose = { flow = null },
                    weekAdjust = { onBack -> WeekAdjustScreen(container, weekViewModel.weekStart, dateKey, onBack) },
                )
            }

            // Closing the day leads straight into concretizing and then planning,
            // which is the order `Planungsphase.md` puts them in.
            AppFlow.Reevaluation -> ReevaluationScreen(
                viewModel = viewModel(key = "reeval-$dateKey", factory = reevaluationViewModelFactory(container)),
                onClose = { flow = AppFlow.Concretize },
            )

            AppFlow.Concretize -> ConcretizeScreen(
                viewModel = viewModel(key = "concretize-$dateKey", factory = concretizeViewModelFactory(container)),
                onDone = { flow = AppFlow.Calendar },
            )

            // Between the concretizing step and the planner, which is the whole
            // point of it: an appointment settled *before* the day is filled is
            // part of the picture, one settled after it is a collision. It skips
            // itself when the calendar has nothing new, so an evening without
            // appointments runs exactly as it always did.
            AppFlow.Calendar -> CalendarStepScreen(
                viewModel = viewModel(
                    key = "calendar-day-$dateKey",
                    factory = calendarViewModelFactory(
                        container = container,
                        from = currentDate().plus(DatePeriod(days = 1)),
                        to = currentDate().plus(DatePeriod(days = 1)),
                    ),
                ),
                onDone = { flow = AppFlow.Planner },
            )

            AppFlow.Vacation -> VacationScreen(
                viewModel = viewModel(key = "vacation-$dateKey", factory = vacationViewModelFactory(container)),
                onClose = { flow = null },
            )

            null -> Unit
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            tabState.SaveableStateProvider(tab.name) {
            when (tab) {
                EtaTab.Today -> DashboardScreen(
                    viewModel = viewModel(key = "dashboard-$dateKey", factory = dashboardViewModelFactory(container)),
                    quickAddViewModel = viewModel(
                        key = "quickadd-$dateKey",
                        factory = quickAddViewModelFactory(container),
                    ),
                    // The very view model "Heute umplanen" opens with, by key.
                    plannerViewModel = viewModel(
                        key = "planner-today-$dateKey",
                        factory = plannerViewModelFactory(container, PlannerDay.TODAY),
                    ),
                    onPlanTomorrow = { flow = AppFlow.Planner },
                    onReplanToday = { flow = AppFlow.PlannerToday },
                    onPlanWeek = { flow = AppFlow.WeekPlanner },
                    onCloseDay = { flow = AppFlow.Reevaluation },
                    weekPage = {
                        WeekTabScreen(
                            viewModel = viewModel(
                                key = "week-tab-$dateKey",
                                factory = weekTabViewModelFactory(container),
                            ),
                            // The same instance the dashboard's list edits through.
                            plannerViewModel = viewModel(
                                key = "planner-today-$dateKey",
                                factory = plannerViewModelFactory(container, PlannerDay.TODAY),
                            ),
                        )
                    },
                )

                EtaTab.Lists -> SmartListsScreen(
                    viewModel = viewModel(key = "lists-$dateKey", factory = smartListsViewModelFactory(container)),
                    onOpenPlanner = { flow = AppFlow.Planner },
                    onPlanWeek = { flow = AppFlow.WeekTopUp },
                    onOpenToday = { minute ->
                        flow = AppFlow.PlannerToday
                        plannerFocus = minute
                    },
                    tutorialOffer = tutorialState.listsOffer,
                    onAcceptTutorial = {
                        container.tutorialStore.answerListsOffer()
                        container.tutorialStore.request(TutorialId.LISTS.name)
                    },
                    onDeclineTutorial = container.tutorialStore::answerListsOffer,
                )

                EtaTab.Contracts -> ContractsScreen(
                    viewModel = viewModel(key = "contracts-$dateKey", factory = contractsViewModelFactory(container)),
                )

                EtaTab.Reminders -> RemindersScreen(
                    viewModel = viewModel(key = "reminders-$dateKey", factory = remindersViewModelFactory(container)),
                )

                EtaTab.Growth -> GrowthTasksScreen(
                    viewModel = viewModel(
                        key = "growth-$dateKey",
                        factory = growthTasksViewModelFactory(container),
                    ),
                )

                EtaTab.Rewards -> RewardsScreen(
                    viewModel = viewModel(
                        key = "rewards-$dateKey",
                        factory = rewardsViewModelFactory(container),
                    ),
                )

                EtaTab.Statistics -> StatisticsScreen(
                    viewModel = viewModel(
                        key = "statistics-$dateKey",
                        factory = statisticsViewModelFactory(container),
                    ),
                )

                EtaTab.Notes -> NotesScreen(
                    viewModel = viewModel(
                        key = "notes-$dateKey",
                        factory = notesViewModelFactory(container),
                    ),
                )

                EtaTab.Settings -> SettingsScreen(
                    viewModel = viewModel(factory = settingsViewModelFactory(container)),
                    calendarViewModel = viewModel(
                        factory = calendarSettingsViewModelFactory(container),
                    ),
                    design = design,
                    onDesignChange = { chosen ->
                        container.designStore.select(chosen)
                        QuickAddWidgetProvider.refresh(context)
                    },
                    onBrightnessChange = { chosen ->
                        container.designStore.select(chosen)
                        QuickAddWidgetProvider.refresh(context)
                    },
                    onAccentChange = container.designStore::selectAccent,
                    onTabStyleChange = { chosen -> container.designStore.select(chosen) },
                    tabs = tabs.map { it.label to it.icon },
                    onDebugReset = rootViewModel::resetEverything,
                    onOpenVacation = { flow = AppFlow.Vacation },
                    onStartTutorial = { id -> container.tutorialStore.request(id?.name) },
                )
            }
            }
        }

        EtaTabBar(
            items = tabs.map { EtaTabItem(it.name, it.label, it.icon) },
            style = design.tabStyle,
            selectedId = tab.name,
            onSelect = { id -> savedTab = EtaTab.valueOf(id) },
        )
    }
}

/**
 * The current date, re-read every minute.
 *
 * A minute is fine granularity for a day boundary and costs nothing; anything
 * shorter would just wake the composition more often for the same answer.
 */
@Composable
private fun rememberDateKey(): String {
    var date by remember { mutableStateOf(currentDate()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            date = currentDate()
        }
    }
    return date.toString()
}

private fun currentDate(): LocalDate =
    Clock.System.todayIn(TimeZone.currentSystemDefault())

/**
 * Places you can always come back to.
 *
 * Erinnerungen is a tab rather than a flow: a list of what is still to come is a
 * place to look things up, not something one is in the middle of.
 */
private enum class EtaTab(val label: String, val icon: EtaIcon) {
    /**
     * Called "Heute" until it grew tabs of its own, one of which carries that
     * name now. The entry keeps its, being what a saved tab is restored by.
     */
    Today("Dashboard", EtaIcon.HOME),
    Lists("Listen", EtaIcon.LIST),
    Contracts("Verträge", EtaIcon.CONTRACT),
    Reminders("Erinnerungen", EtaIcon.BELL),

    /**
     * Its own tab because of the one thing it has that no list can express: the
     * **order** growth tasks hold, which is what settles a slot two of them both
     * want.
     */
    Growth("Ziele", EtaIcon.TARGET),

    /** Long-term rewards, filled by the evening. Before the settings, which stay last. */
    Rewards("Belohn-o-mat", EtaIcon.GIFT),

    /** Step 43: what the days add up to, and notes that can become ToDos. */
    Statistics("Statistiken", EtaIcon.CHART),
    Notes("Notiz-Merger", EtaIcon.NOTE),

    /** Far right, as the user asked: it is the tab opened least. */
    Settings("Einstellungen", EtaIcon.SLIDERS),
}

/** Whether the tab's feature is switched on; most tabs have none and always are. */
private fun EtaTab.isShown(features: Features): Boolean = when (this) {
    EtaTab.Contracts -> features.contracts
    EtaTab.Growth -> features.growthTasks
    EtaTab.Rewards -> features.rewards
    EtaTab.Statistics -> features.statistics
    EtaTab.Notes -> features.notes
    else -> true
}

/**
 * Savers for the two, by name.
 *
 * A null flow saves nothing, which restores as the initial null — exactly right:
 * "no flow" is what the screen comes back to anyway.
 */
private val TAB_SAVER: Saver<EtaTab, Any> = Saver(
    save = { it.name },
    restore = { runCatching { EtaTab.valueOf(it as String) }.getOrNull() },
)

private val FLOW_SAVER: Saver<AppFlow?, Any> = Saver(
    save = { it?.name },
    restore = { runCatching { AppFlow.valueOf(it as String) }.getOrNull() },
)

/** Things you are in the middle of. They cover the bar until they are done. */
private enum class AppFlow {
    Planner,
    /** The evening's look at what the calendar picked up during the day. */
    Calendar,
    /** The day planner turned on today, for changing a plan while it runs. */
    PlannerToday,
    WeekPlanner,
    WeekTopUp,
    Reevaluation,
    Concretize,
    Vacation,
}

/**
 * Asks for the notification permission once, on the versions that have one.
 *
 * Refusing costs the reminder, not the mechanism: the alarm still fires and still
 * reschedules itself, it just has nothing to show.
 */
@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Manual factories, matching the container-based wiring. Swapping in Hilt later
 * would replace these without touching the view models themselves.
 */
private fun rootViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            RootViewModel(
                container.setupRepository,
                container.planningAlarmCoordinator,
                container.taskStartCoordinator,
                container.reminderCoordinator,
                container.scheduleMaintenance,
                container.weekPlanningService,
                container.tutorialStore,
            )
        }
    }

private fun setupViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { SetupViewModel(container.setupRepository, container.recurringTaskService) }
    }

internal fun dashboardViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DashboardViewModel(
                itemRepository = container.itemRepository,
                planRepository = container.planRepository,
                pointsRepository = container.pointsRepository,
                contractRepository = container.contractRepository,
                conflictRepository = container.conflictRepository,
                setupRepository = container.setupRepository,
                subtaskRepository = container.subtaskRepository,
                taskStartCoordinator = container.taskStartCoordinator,
                clock = container.clock,
                // Not in the practice app: its day is simulated.
                sleepLogRepository = container.sleepLogRepository.takeIf { !container.sandbox },
                nightService = container.nightService.takeIf { !container.sandbox },
            )
        }
    }

internal fun plannerViewModelFactory(
    container: AppContainer,
    day: PlannerDay,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DayPlannerViewModel(
                itemRepository = container.itemRepository,
                planRepository = container.planRepository,
                subtaskRepository = container.subtaskRepository,
                subtaskGroupService = container.subtaskGroupService,
                setupRepository = container.setupRepository,
                scheduleMaintenance = container.scheduleMaintenance,
                weekPlanningService = container.weekPlanningService,
                pointsRepository = container.pointsRepository,
                nightService = container.nightService,
                wakeAlarmCoordinator = container.wakeAlarmCoordinator,
                day = day,
                clock = container.clock,
            )
        }
    }

internal fun weekPlannerViewModelFactory(
    container: AppContainer,
    scope: WeekScope,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            WeekPlannerViewModel(
                scope = scope,
                weekPlanningService = container.weekPlanningService,
                itemRepository = container.itemRepository,
                planRepository = container.planRepository,
                setupRepository = container.setupRepository,
                scheduleMaintenance = container.scheduleMaintenance,
                clock = container.clock,
            )
        }
    }

/**
 * The week a planning phase is laying out, as the timetable — "Anpassungen
 * vornehmen".
 *
 * The dashboard's own "Woche" on other days: the seven from the first day of the
 * phase's week, and "Aktuell" only, since what the phase is about is **this**
 * week — its appointments to be seen, its standing tasks to be moved for once.
 * The standing week is changed from the dashboard, not from inside a phase that
 * plans one week of it.
 */
@Composable
private fun WeekAdjustScreen(
    container: AppContainer,
    weekStart: LocalDate,
    dateKey: String,
    onBack: () -> Unit,
) {
    WeekTabScreen(
        viewModel = viewModel(
            key = "week-adjust-$weekStart",
            factory = weekTabViewModelFactory(container, from = weekStart),
        ),
        plannerViewModel = viewModel(
            key = "planner-today-$dateKey",
            factory = plannerViewModelFactory(container, PlannerDay.TODAY),
        ),
        onlyView = WeekView.CURRENT,
        header = {
            Row(
                modifier = Modifier.padding(
                    start = EtaTheme.spacing.lg,
                    end = EtaTheme.spacing.lg,
                    top = EtaTheme.spacing.lg,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
            ) {
                EtaButton(text = "‹ Woche planen", style = EtaButtonStyle.Secondary, onClick = onBack)
                EtaText(text = "Anpassungen", style = EtaTheme.typography.title)
            }
        },
    )
}

private fun weekTabViewModelFactory(
    container: AppContainer,
    from: LocalDate? = null,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            WeekTabViewModel(
                nightService = container.nightService,
                wakeAlarmCoordinator = container.wakeAlarmCoordinator,
                taskStartCoordinator = container.taskStartCoordinator,
                recurringTaskService = container.recurringTaskService,
                planRepository = container.planRepository,
                setupRepository = container.setupRepository,
                subtaskRepository = container.subtaskRepository,
                itemRepository = container.itemRepository,
                clock = container.clock,
                from = from,
            )
        }
    }

internal fun concretizeViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ConcretizeViewModel(
                itemRepository = container.itemRepository,
                scheduleMaintenance = container.scheduleMaintenance,
                subtaskRepository = container.subtaskRepository,
                setupRepository = container.setupRepository,
                clock = container.clock,
            )
        }
    }

private fun vacationViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            VacationViewModel(container.vacationRepository, container.itemRepository)
        }
    }

/**
 * The calendar step, over whichever stretch its host is planning.
 *
 * The weekly phase builds its own over the week; this one is for the evening and
 * covers tomorrow alone.
 */
private fun calendarViewModelFactory(
    container: AppContainer,
    from: LocalDate,
    to: LocalDate,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            CalendarEventsViewModel(
                calendarRepository = container.calendarRepository,
                syncService = container.calendarSyncService,
                importService = container.calendarImportService,
                scheduleMaintenance = container.scheduleMaintenance,
                from = from,
                to = to,
            )
        }
    }

private fun calendarSettingsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            CalendarSettingsViewModel(
                repository = container.calendarRepository,
                syncService = container.calendarSyncService,
            )
        }
    }

private fun settingsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            SettingsViewModel(
                setupRepository = container.setupRepository,
                backupService = container.backupService,
                catchUpService = container.catchUpService,
                phaseService = container.planningPhaseService,
                wakeAlarmCoordinator = container.wakeAlarmCoordinator,
                taskStartCoordinator = container.taskStartCoordinator,
                planRepository = container.planRepository,
                reminderRepository = container.reminderRepository,
            )
        }
    }

internal fun smartListsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            SmartListsViewModel(
                itemRepository = container.itemRepository,
                planRepository = container.planRepository,
                scheduleMaintenance = container.scheduleMaintenance,
                recurringTaskService = container.recurringTaskService,
                reevaluationService = container.reevaluationService,
                subtaskRepository = container.subtaskRepository,
                setupRepository = container.setupRepository,
                weekPlanningService = container.weekPlanningService,
                clock = container.clock,
            )
        }
    }

internal fun rewardsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            RewardsViewModel(
                rewardRepository = container.rewardRepository,
                recurringTaskService = container.recurringTaskService,
            )
        }
    }

internal fun statisticsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            StatisticsViewModel(
                planRepository = container.planRepository,
                sleepLogRepository = container.sleepLogRepository,
                recurringTaskService = container.recurringTaskService,
                setupRepository = container.setupRepository,
                nightService = container.nightService,
                clock = container.clock,
            )
        }
    }

internal fun notesViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            NotesViewModel(
                noteRepository = container.noteRepository,
                itemRepository = container.itemRepository,
                clock = container.clock,
            )
        }
    }

internal fun growthTasksViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            GrowthTasksViewModel(
                growthService = container.growthService,
                recurringTaskService = container.recurringTaskService,
                subtaskRepository = container.subtaskRepository,
                itemRepository = container.itemRepository,
            )
        }
    }

private fun remindersViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            RemindersViewModel(
                repository = container.reminderRepository,
                coordinator = container.reminderCoordinator,
            )
        }
    }

internal fun contractsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { ContractsViewModel(container.contractRepository, clock = container.clock) }
    }

internal fun reevaluationViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ReevaluationViewModel(
                reevaluationService = container.reevaluationService,
                planRepository = container.planRepository,
                itemRepository = container.itemRepository,
                dayClosingService = container.dayClosingService,
                subtaskRepository = container.subtaskRepository,
                clock = container.clock,
            )
        }
    }
