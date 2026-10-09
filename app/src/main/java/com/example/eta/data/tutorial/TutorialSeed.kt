package com.example.eta.data.tutorial

import com.example.eta.data.local.ItemDao
import com.example.eta.data.local.SetupDao
import com.example.eta.data.repository.PlanRepository
import com.example.eta.domain.planning.DAYS_PER_WEEK
import com.example.eta.domain.tutorial.tutorialItems
import com.example.eta.domain.tutorial.tutorialSetup
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Lays the tutorial's example day into a practice database.
 *
 * Only a sandbox `AppContainer` builds one — the real container's is null — so
 * the example tasks have no route into the user's own data.
 *
 * The setup row is written directly rather than through `SetupRepository.complete`:
 * that would lay down the bed preparation and the morning as well, and the day
 * the tutorial talks about has exactly four tasks on it.
 */
class TutorialSeed(
    private val setupDao: SetupDao,
    private val itemDao: ItemDao,
    private val planRepository: PlanRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    suspend fun lay() {
        val now = clock.now()
        val today = clock.todayIn(timeZone)
        val items = tutorialItems(today.dayOfWeek, now)

        setupDao.upsert(tutorialSetup(now))
        itemDao.upsertAll(items)
        // The week ahead as well, so the planner and the free-hour count have
        // tomorrow to show before any screen has topped the schedule up.
        planRepository.materializeRecurring(
            definitions = items,
            from = today,
            to = today.plus(DatePeriod(days = DAYS_PER_WEEK)),
        )
    }
}
