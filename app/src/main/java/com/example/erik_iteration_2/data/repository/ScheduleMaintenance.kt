package com.example.erik_iteration_2.data.repository

import com.example.erik_iteration_2.data.local.ItemDao
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** How far ahead the standing schedule is kept materialized. */
const val SCHEDULE_HORIZON_DAYS = 21

/**
 * Keeps the standing schedule laid down ahead of today.
 *
 * Without this the recurring tasks quietly run out: the setup materialized a
 * fortnight and nothing extended it, so two weeks after answering the
 * questionnaire the day planner, the free-hour maths and the "now" box would all
 * have gone empty with no error to explain it.
 *
 * Cheap to call often — `expandRecurring` skips what already exists — so it runs
 * whenever the app starts and at the top of each planning phase, which is where
 * the concept always said expansion belonged.
 */
class ScheduleMaintenance(
    private val itemDao: ItemDao,
    private val planRepository: PlanRepository,
    private val vacationRepository: VacationRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    /** Materializes from today to the horizon. Returns how many blocks were added. */
    suspend fun topUp(): Int = topUpUntil(today().plus(DatePeriod(days = SCHEDULE_HORIZON_DAYS)))

    /** Materializes at least as far as [until], for a range that reaches further. */
    suspend fun topUpUntil(until: LocalDate): Int {
        val today = today()
        val horizon = maxOf(until, today.plus(DatePeriod(days = SCHEDULE_HORIZON_DAYS)))
        return planRepository.materializeRecurring(
            definitions = itemDao.findRecurringDefinitions(),
            from = today,
            to = horizon,
            vacations = vacationRepository.plansFrom(today),
        )
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZone).date
}
