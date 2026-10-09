package com.example.eta.ui.root

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.eta.ui.rewards.RewardsScreen
import com.example.eta.ui.rewards.RewardsViewModel
import com.example.eta.ui.components.LocalPointsVisible
import androidx.compose.runtime.CompositionLocalProvider
import com.example.eta.ui.settings.CalendarSettingsViewModel
import com.example.eta.ui.settings.SettingsScreen
import com.example.eta.ui.settings.SettingsViewModel
import com.example.eta.ui.setup.SetupScreen
import com.example.eta.ui.setup.SetupViewModel
import com.example.eta.ui.vacation.VacationScreen
import com.example.eta.ui.vacation.VacationViewModel
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

    when (destination) {
        RootDestination.Loading -> EtaScreen {}

        RootDestination.Setup -> SetupScreen(
            viewModel = viewModel(factory = setupViewModelFactory(container)),
        )

        RootDestination.Dashboard -> {
            // Handed down rather than read by each screen: the switch hides
            // figures in a dozen unrelated places. True until the setup arrives,
            // which is what it was before there was a switch.
            val setup by container.setupRepository.observe()
                .collectAsStateWithLifecycle(initialValue = null)
            CompositionLocalProvider(LocalPointsVisible provides (setup?.pointsSystem != false)) {
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

    var tab by rememberSaveable(stateSaver = TAB_SAVER) { mutableStateOf(EtaTab.Today) }
    var flow by rememberSaveable(stateSaver = FLOW_SAVER) { mutableStateOf<AppFlow?>(null) }

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
    BackHandler(enabled = flow == null && tab != EtaTab.Today) { tab = EtaTab.Today }

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
                )

                EtaTab.Lists -> SmartListsScreen(
                    viewModel = viewModel(key = "lists-$dateKey", factory = smartListsViewModelFactory(container)),
                    onOpenPlanner = { flow = AppFlow.Planner },
                    onPlanWeek = { flow = AppFlow.WeekTopUp },
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
                    onDebugReset = rootViewModel::resetEverything,
                    onOpenVacation = { flow = AppFlow.Vacation },
                )
            }
        }

        EtaTabBar(
            items = EtaTab.entries.map { EtaTabItem(it.name, it.label) },
            selectedId = tab.name,
            onSelect = { id -> tab = EtaTab.valueOf(id) },
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
private enum class EtaTab(val label: String) {
    Today("Heute"),
    Lists("Listen"),
    Contracts("Verträge"),
    Reminders("Erinnerungen"),

    /**
     * Its own tab because of the one thing it has that no list can express: the
     * **order** growth tasks hold, which is what settles a slot two of them both
     * want.
     */
    Growth("Growth-Tasks"),

    /** Long-term rewards, filled by the evening. Before the settings, which stay last. */
    Rewards("Belohn-o-mat"),

    /** Far right, as the user asked: it is the tab opened least. */
    Settings("Einstellungen"),
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
            )
        }
    }

private fun setupViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { SetupViewModel(container.setupRepository) }
    }

private fun dashboardViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
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
            )
        }
    }

private fun plannerViewModelFactory(
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
                day = day,
            )
        }
    }

private fun weekPlannerViewModelFactory(
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
            )
        }
    }

private fun concretizeViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ConcretizeViewModel(
                itemRepository = container.itemRepository,
                scheduleMaintenance = container.scheduleMaintenance,
                subtaskRepository = container.subtaskRepository,
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

private fun smartListsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
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
            )
        }
    }

private fun rewardsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            RewardsViewModel(
                rewardRepository = container.rewardRepository,
                recurringTaskService = container.recurringTaskService,
            )
        }
    }

private fun growthTasksViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
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

private fun contractsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { ContractsViewModel(container.contractRepository) }
    }

private fun reevaluationViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ReevaluationViewModel(
                reevaluationService = container.reevaluationService,
                planRepository = container.planRepository,
                itemRepository = container.itemRepository,
                dayClosingService = container.dayClosingService,
                subtaskRepository = container.subtaskRepository,
            )
        }
    }
