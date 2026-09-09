package com.example.erik_iteration_2.di

import android.content.Context
import androidx.room3.Room
import com.example.erik_iteration_2.alarm.PlanningAlarmCoordinator
import com.example.erik_iteration_2.alarm.TaskStartCoordinator
import com.example.erik_iteration_2.alarm.WakeAlarmCoordinator
import com.example.erik_iteration_2.data.backup.BackupService
import com.example.erik_iteration_2.data.local.ErikDatabase
import com.example.erik_iteration_2.data.local.MIGRATION_1_2
import com.example.erik_iteration_2.data.local.MIGRATION_2_3
import com.example.erik_iteration_2.data.local.MIGRATION_3_4
import com.example.erik_iteration_2.data.local.MIGRATION_4_5
import com.example.erik_iteration_2.data.local.MIGRATION_5_6
import com.example.erik_iteration_2.data.local.MIGRATION_6_7
import com.example.erik_iteration_2.data.local.MIGRATION_7_8
import com.example.erik_iteration_2.data.local.MIGRATION_8_9
import com.example.erik_iteration_2.data.local.MIGRATION_10_11
import com.example.erik_iteration_2.data.local.MIGRATION_11_12
import com.example.erik_iteration_2.data.local.MIGRATION_12_13
import com.example.erik_iteration_2.data.local.MIGRATION_9_10
import com.example.erik_iteration_2.data.repository.CatchUpService
import com.example.erik_iteration_2.data.repository.ContractRepository
import com.example.erik_iteration_2.data.repository.DayClosingService
import com.example.erik_iteration_2.data.repository.ItemRepository
import com.example.erik_iteration_2.data.repository.PlanRepository
import com.example.erik_iteration_2.data.repository.PlanningPhaseService
import com.example.erik_iteration_2.data.repository.PointsRepository
import com.example.erik_iteration_2.data.repository.ReevaluationService
import com.example.erik_iteration_2.data.repository.ScheduleMaintenance
import com.example.erik_iteration_2.data.repository.SetupRepository
import com.example.erik_iteration_2.data.repository.VacationRepository
import com.example.erik_iteration_2.data.repository.WeekPlanningService

/**
 * Manual dependency wiring.
 *
 * Deliberately not a DI framework yet: the graph is small and a container keeps
 * the build free of another annotation processor. Swapping this for Hilt later
 * only touches construction sites, not the repositories themselves.
 */
class AppContainer(
    context: Context,
    /** Asked by the planning alarm, which stays quiet while the app is open. */
    isAppInForeground: () -> Boolean = { false },
) {
    private val appContext = context.applicationContext

    private val database: ErikDatabase = Room
        .databaseBuilder(
            context.applicationContext,
            ErikDatabase::class.java,
            ErikDatabase.NAME,
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
        .build()

    val backupService: BackupService = BackupService(appContext)

    val itemRepository: ItemRepository = ItemRepository(database.itemDao())

    val pointsRepository: PointsRepository = PointsRepository(database.pointsDao())

    val planRepository: PlanRepository = PlanRepository(
        database.plannedBlockDao(),
        database.dayPlanDao(),
    )

    val dayClosingService: DayClosingService = DayClosingService(
        database.itemDao(),
        database.plannedBlockDao(),
    )

    val vacationRepository: VacationRepository = VacationRepository(
        database.vacationDao(),
        database.plannedBlockDao(),
    )

    val setupRepository: SetupRepository = SetupRepository(
        database.setupDao(),
        database.itemDao(),
        database.resetDao(),
        planRepository,
        vacationRepository,
    )

    val scheduleMaintenance: ScheduleMaintenance = ScheduleMaintenance(
        itemDao = database.itemDao(),
        planRepository = planRepository,
        vacationRepository = vacationRepository,
    )

    val contractRepository: ContractRepository = ContractRepository(database.contractDao())

    val weekPlanningService: WeekPlanningService = WeekPlanningService(
        pointsDao = database.pointsDao(),
        journalDao = database.journalDao(),
        itemRepository = itemRepository,
        setupRepository = setupRepository,
    )

    val reevaluationService: ReevaluationService = ReevaluationService(
        blockDao = database.plannedBlockDao(),
        journalDao = database.journalDao(),
        contractRepository = contractRepository,
        itemRepository = itemRepository,
        planRepository = planRepository,
        pointsRepository = pointsRepository,
        setupRepository = setupRepository,
    )

    val catchUpService: CatchUpService = CatchUpService(
        dayPlanDao = database.dayPlanDao(),
        blockDao = database.plannedBlockDao(),
        reevaluationService = reevaluationService,
    )

    val planningPhaseService: PlanningPhaseService = PlanningPhaseService(
        setupRepository = setupRepository,
        planRepository = planRepository,
        weekPlanningService = weekPlanningService,
    )

    val planningAlarmCoordinator: PlanningAlarmCoordinator = PlanningAlarmCoordinator(
        context = appContext,
        phaseService = planningPhaseService,
        isAppInForeground = isAppInForeground,
    )

    /**
     * Announces the start of every planned block. Takes no foreground flag: a
     * task beginning is about the world, not about which screen is open.
     */
    val taskStartCoordinator: TaskStartCoordinator = TaskStartCoordinator(
        context = appContext,
        planRepository = planRepository,
    )

    /** Off unless the setup says otherwise; rings at the setup's wake time. */
    val wakeAlarmCoordinator: WakeAlarmCoordinator = WakeAlarmCoordinator(
        context = appContext,
        setupRepository = setupRepository,
    )
}
