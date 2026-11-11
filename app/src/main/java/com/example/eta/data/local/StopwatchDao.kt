package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.StopwatchTime
import kotlinx.coroutines.flow.Flow

/** The stopwatch's times (step 56). */
@Dao
interface StopwatchDao {

    @Upsert
    suspend fun upsert(time: StopwatchTime)

    @Query("SELECT * FROM stopwatch_times WHERE id = :id")
    suspend fun find(id: String): StopwatchTime?

    /** The ones still being taken, oldest first. */
    @Query("SELECT * FROM stopwatch_times WHERE endedAt IS NULL ORDER BY createdAt")
    fun observeRunning(): Flow<List<StopwatchTime>>

    @Query("SELECT * FROM stopwatch_times WHERE endedAt IS NOT NULL ORDER BY date DESC, createdAt DESC")
    fun observeEnded(): Flow<List<StopwatchTime>>

    @Query("DELETE FROM stopwatch_times WHERE id = :id")
    suspend fun delete(id: String)
}
