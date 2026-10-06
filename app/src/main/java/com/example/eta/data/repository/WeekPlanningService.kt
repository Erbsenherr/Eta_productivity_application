package com.example.eta.data.repository

import com.example.eta.data.local.JournalDao
import com.example.eta.data.local.PointsDao
import com.example.eta.domain.model.JournalEntry
import com.example.eta.domain.model.JournalKind
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.PointsTransaction
import com.example.eta.domain.planning.DAYS_PER_WEEK
import com.example.eta.domain.reward.isInflationDue
import com.example.eta.domain.reward.weeklyInflationOn
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * Which stretch the weekly planner is looking at.
 *
 * [SCHEDULED] is the phase proper, planning the week that starts tomorrow.
 * [MIDWEEK] is a top-up: the rest of the cycle already running. They differ only
 * in the range, but the range is what the free-hour count is computed over, so
 * confusing the two would quietly show hours that belong to a different week.
 */
enum class WeekScope { SCHEDULED, MIDWEEK }

/** What the devaluation would take, and whether it is due at all. */
data class InflationPreview(
    val balance: Double,
    val loss: Double,
    val alreadyApplied: Boolean,
) {
    val remaining: Double get() = balance - loss
}

/**
 * The weekly planning phase, as far as it touches more than one table.
 *
 * The week being planned is the seven days *after* the planning day, rather than
 * a calendar week: the user picks which weekday they plan on, and the plan should
 * cover the seven days that follow whenever that is.
 */
class WeekPlanningService(
    private val pointsDao: PointsDao,
    private val journalDao: JournalDao,
    private val itemRepository: ItemRepository,
    private val setupRepository: SetupRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    fun today(): LocalDate = clock.todayIn(timeZone)

    fun weekStart(): LocalDate = today().plus(DatePeriod(days = 1))

    fun weekEnd(): LocalDate = today().plus(DatePeriod(days = DAYS_PER_WEEK))

    /** The day the cycle running now began: the last weekly planning day. */
    suspend fun cycleStart(): LocalDate {
        val setup = setupRepository.find() ?: return today()
        var date = today()
        repeat(DAYS_PER_WEEK) {
            if (date.dayOfWeek == setup.weeklyPlanningDay) return date
            date = date.minus(DatePeriod(days = 1))
        }
        return today()
    }

    /**
     * Whether the devaluation still owes this week, and what it would cost.
     *
     * "Already applied" is read off the ledger rather than stored as a flag: the
     * ledger is the record of what happened, and a second source of truth could
     * disagree with it.
     */
    suspend fun inflationPreview(): InflationPreview {
        val balance = pointsDao.balance()
        val setup = setupRepository.find()
        val last = pointsDao.findLatest(PointsReason.INFLATION)
        val lastDate = last?.occurredAt?.toLocalDateTime(timeZone)?.date

        val due = setup != null && isInflationDue(
            lastApplied = lastDate,
            since = setup.completedAt.toLocalDateTime(timeZone).date,
            weekday = setup.inflationWeekday,
            today = today(),
        )

        // Once it has been booked — which is the normal case by the time anyone
        // looks — the interesting figure is what it cost, so the last row is
        // reported backwards: what the balance was, and what came off it.
        if (!due) {
            val booked = -(last?.amount ?: 0.0)
            return InflationPreview(balance = balance + booked, loss = booked, alreadyApplied = true)
        }

        return InflationPreview(balance = balance, loss = weeklyInflationOn(balance), alreadyApplied = false)
    }

    /**
     * Books the devaluation if its weekday has come round since the last one.
     *
     * Tied to the calendar rather than to opening a screen: the week turns over
     * whether or not anyone looks. Called wherever the app wakes up, so it lands
     * on the first launch after the due day.
     *
     * A due week with nothing saved still writes a zero row. Without it the ledger
     * would keep reading "never applied" and would charge the moment the account
     * first went positive, days out of step with the week.
     */
    suspend fun applyInflation(): Double {
        val preview = inflationPreview()
        if (preview.alreadyApplied) return 0.0
        if (preview.loss <= 0.0) {
            pointsDao.insert(
                PointsTransaction(
                    amount = 0.0,
                    reason = PointsReason.INFLATION,
                    occurredAt = clock.now(),
                    note = "nichts angespart",
                ),
            )
            return 0.0
        }

        pointsDao.insert(
            PointsTransaction(
                amount = -preview.loss,
                reason = PointsReason.INFLATION,
                occurredAt = clock.now(),
                note = "30% von ${preview.balance}",
            ),
        )
        return preview.loss
    }

    /**
     * The Sperrliste sweep belongs to the planning phases, and this is the one
     * that runs weekly — the ban is a one-month rule, so a weekly pass is timely
     * enough and a daily one would be noise.
     */
    suspend fun sweepStaleCollectionItems(): Int = itemRepository.sweepStaleCollectionItems()

    /** The weekly evaluation field: what should be better next week. */
    suspend fun recordEvaluation(question: String, answer: String) {
        if (answer.isBlank()) return
        journalDao.insertAll(
            listOf(
                JournalEntry(
                    kind = JournalKind.WEEKLY,
                    date = today(),
                    question = question,
                    answer = answer,
                    createdAt = clock.now(),
                ),
            ),
        )
    }
}
