package com.example.erik_iteration_2.ui.root

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.erik_iteration_2.data.repository.WeekScope
import com.example.erik_iteration_2.di.AppContainer
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.example.erik_iteration_2.domain.planning.PlanningPhase
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikTabBar
import com.example.erik_iteration_2.ui.components.ErikTabItem
import com.example.erik_iteration_2.ui.concretize.ConcretizeScreen
import com.example.erik_iteration_2.ui.concretize.ConcretizeViewModel
import com.example.erik_iteration_2.ui.contracts.ContractsScreen
import com.example.erik_iteration_2.ui.contracts.ContractsViewModel
import com.example.erik_iteration_2.ui.dashboard.DashboardScreen
import com.example.erik_iteration_2.ui.dashboard.DashboardViewModel
import com.example.erik_iteration_2.ui.lists.SmartListsScreen
import com.example.erik_iteration_2.ui.lists.SmartListsViewModel
import com.example.erik_iteration_2.ui.planner.DayPlannerScreen
import com.example.erik_iteration_2.ui.planner.DayPlannerViewModel
import com.example.erik_iteration_2.ui.reevaluation.ReevaluationScreen
import com.example.erik_iteration_2.ui.reevaluation.ReevaluationViewModel
import com.example.erik_iteration_2.ui.settings.SettingsScreen
import com.example.erik_iteration_2.ui.settings.SettingsViewModel
import com.example.erik_iteration_2.ui.setup.SetupScreen
import com.example.erik_iteration_2.ui.setup.SetupViewModel
import com.example.erik_iteration_2.ui.vacation.VacationScreen
import com.example.erik_iteration_2.ui.vacation.VacationViewModel
import com.example.erik_iteration_2.ui.weekplanner.WeekPlannerScreen
import com.example.erik_iteration_2.ui.weekplanner.WeekPlannerViewModel

/**
 * Picks the first screen: the questionnaire until it has been answered, the
 * dashboard afterwards. Finishing the setup flips this by itself, since the
 * destination is derived from the stored answers.
 */
@Composable
fun ErikApp(
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
        RootDestination.Loading -> ErikScreen {}

        RootDestination.Setup -> SetupScreen(
            viewModel = viewModel(factory = setupViewModelFactory(container)),
        )

        RootDestination.Dashboard -> MainScaffold(
            container = container,
            rootViewModel = rootViewModel,
            openPhase = openPhase,
            onOpenHandled = onOpenHandled,
        )
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
    var tab by remember { mutableStateOf(ErikTab.Today) }
    var flow by remember { mutableStateOf<AppFlow?>(null) }

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
    BackHandler(enabled = flow == null && tab != ErikTab.Today) { tab = ErikTab.Today }

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
                viewModel = viewModel(key = "planner-$dateKey", factory = plannerViewModelFactory(container)),
                onClose = { flow = null },
                onTopUpWeek = { flow = AppFlow.WeekTopUp },
            )

            AppFlow.WeekPlanner -> WeekPlannerScreen(
                viewModel = viewModel(
                    key = "week-$dateKey",
                    factory = weekPlannerViewModelFactory(container, WeekScope.SCHEDULED),
                ),
                onClose = { flow = null },
            )

            // Same screen, different stretch: adding to the week already running.
            AppFlow.WeekTopUp -> WeekPlannerScreen(
                viewModel = viewModel(
                    key = "week-topup-$dateKey",
                    factory = weekPlannerViewModelFactory(container, WeekScope.MIDWEEK),
                ),
                onClose = { flow = null },
            )

            // Closing the day leads straight into concretizing and then planning,
            // which is the order `Planungsphase.md` puts them in.
            AppFlow.Reevaluation -> ReevaluationScreen(
                viewModel = viewModel(key = "reeval-$dateKey", factory = reevaluationViewModelFactory(container)),
                onClose = { flow = AppFlow.Concretize },
            )

            AppFlow.Concretize -> ConcretizeScreen(
                viewModel = viewModel(key = "concretize-$dateKey", factory = concretizeViewModelFactory(container)),
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
                ErikTab.Today -> DashboardScreen(
                    viewModel = viewModel(key = "dashboard-$dateKey", factory = dashboardViewModelFactory(container)),
                    onPlanTomorrow = { flow = AppFlow.Planner },
                    onPlanWeek = { flow = AppFlow.WeekPlanner },
                    onCloseDay = { flow = AppFlow.Reevaluation },
                )

                ErikTab.Lists -> SmartListsScreen(
                    viewModel = viewModel(key = "lists-$dateKey", factory = smartListsViewModelFactory(container)),
                    onOpenPlanner = { flow = AppFlow.Planner },
                    onPlanWeek = { flow = AppFlow.WeekTopUp },
                )

                ErikTab.Contracts -> ContractsScreen(
                    viewModel = viewModel(key = "contracts-$dateKey", factory = contractsViewModelFactory(container)),
                )

                ErikTab.Settings -> SettingsScreen(
                    viewModel = viewModel(factory = settingsViewModelFactory(container)),
                    onDebugReset = rootViewModel::resetEverything,
                    onOpenVacation = { flow = AppFlow.Vacation },
                )
            }
        }

        ErikTabBar(
            items = ErikTab.entries.map { ErikTabItem(it.name, it.label) },
            selectedId = tab.name,
            onSelect = { id -> tab = ErikTab.valueOf(id) },
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
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/** Places you can always come back to. */
private enum class ErikTab(val label: String) {
    Today("Heute"),
    Lists("Listen"),
    Contracts("Verträge"),
    Settings("Einstellungen"),
}

/** Things you are in the middle of. They cover the bar until they are done. */
private enum class AppFlow { Planner, WeekPlanner, WeekTopUp, Reevaluation, Concretize, Vacation }

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
                setupRepository = container.setupRepository,
            )
        }
    }

private fun plannerViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DayPlannerViewModel(
                itemRepository = container.itemRepository,
                planRepository = container.planRepository,
                setupRepository = container.setupRepository,
                scheduleMaintenance = container.scheduleMaintenance,
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
        initializer { ConcretizeViewModel(container.itemRepository) }
    }

private fun vacationViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            VacationViewModel(container.vacationRepository, container.itemRepository)
        }
    }

private fun settingsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { SettingsViewModel(
                container.setupRepository,
                container.backupService,
                container.catchUpService,
            ) }
    }

private fun smartListsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { SmartListsViewModel(container.itemRepository, container.planRepository) }
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
            )
        }
    }
