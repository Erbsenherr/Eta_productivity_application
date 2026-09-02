package com.example.erik_iteration_2.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.example.erik_iteration_2.domain.model.PointsReason
import com.example.erik_iteration_2.domain.model.PointsTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PointsDao {

    @Insert
    suspend fun insert(transaction: PointsTransaction)

    @Insert
    suspend fun insertAll(transactions: List<PointsTransaction>)

    /** The account balance: the whole ledger summed. COALESCE keeps it 0 when empty. */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM points_transactions")
    fun observeBalance(): Flow<Double>

    @Query("SELECT * FROM points_transactions ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PointsTransaction>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM points_transactions")
    suspend fun balance(): Double

    /** The newest movement of one kind — how the weekly inflation knows it already ran. */
    @Query(
        """
        SELECT * FROM points_transactions
        WHERE reason = :reason
        ORDER BY occurredAt DESC
        LIMIT 1
        """,
    )
    suspend fun findLatest(reason: PointsReason): PointsTransaction?
}
