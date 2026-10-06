package com.example.eta.data.repository

import com.example.eta.data.local.PointsDao
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.PointsTransaction
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow

class PointsRepository(
    private val pointsDao: PointsDao,
    private val clock: Clock = Clock.System,
) {

    fun observeBalance(): Flow<Double> = pointsDao.observeBalance()

    fun observeRecent(limit: Int = 30): Flow<List<PointsTransaction>> =
        pointsDao.observeRecent(limit)

    suspend fun record(
        amount: Double,
        reason: PointsReason,
        note: String? = null,
        sourceBlockId: String? = null,
    ) {
        pointsDao.insert(
            PointsTransaction(
                amount = amount,
                reason = reason,
                occurredAt = clock.now(),
                note = note,
                sourceBlockId = sourceBlockId,
            ),
        )
    }
}
