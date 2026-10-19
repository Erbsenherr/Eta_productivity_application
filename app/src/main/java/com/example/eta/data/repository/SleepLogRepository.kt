package com.example.eta.data.repository

import com.example.eta.data.local.TrackingDao
import com.example.eta.domain.model.SleepLog
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * What the user said about getting up and going to bed — see [SleepLog].
 *
 * Each answer is written into the row of its day beside whatever the other end
 * of that day already says, so the morning's answer and the evening's never
 * overwrite one another.
 */
class SleepLogRepository(
    private val dao: TrackingDao,
    private val clock: Clock = Clock.System,
) {

    fun observe(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, SleepLog>> =
        dao.observeSleepLogs(from, to).map { rows -> rows.associateBy { it.date } }

    suspend fun find(date: LocalDate): SleepLog? = dao.findSleepLog(date)

    suspend fun findRange(from: LocalDate, to: LocalDate): List<SleepLog> = dao.findSleepLogs(from, to)

    /**
     * "Bin um … aufgestanden", with the hour that was due that morning and the
     * minutes after it that counted as on time.
     */
    suspend fun recordWake(date: LocalDate, at: LocalTime, due: LocalTime, tolerance: Int? = null) {
        val row = dao.findSleepLog(date) ?: SleepLog(date = date, updatedAt = clock.now())
        dao.upsert(row.copy(wokeAt = at, wakeDue = due, tolerance = tolerance, updatedAt = clock.now()))
    }

    /** "Gehe nun schlafen", or the next day's "Pünktlich" / "Unpünktlich". */
    suspend fun recordBed(date: LocalDate, onTime: Boolean) {
        val row = dao.findSleepLog(date) ?: SleepLog(date = date, updatedAt = clock.now())
        dao.upsert(row.copy(bedOnTime = onTime, updatedAt = clock.now()))
    }
}
