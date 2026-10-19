package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.HabitStreak
import kotlinx.coroutines.flow.Flow

/** The Streaks tab's rows (step 48). */
@Dao
interface StreakDao {

    @Upsert
    suspend fun upsert(streak: HabitStreak)

    @Upsert
    suspend fun upsertAll(streaks: List<HabitStreak>)

    @Query("SELECT * FROM streaks WHERE endedAt IS NULL ORDER BY startedAt")
    fun observeRunning(): Flow<List<HabitStreak>>

    @Query("SELECT * FROM streaks WHERE endedAt IS NULL ORDER BY startedAt")
    suspend fun running(): List<HabitStreak>

    @Query("SELECT * FROM streaks WHERE id = :id")
    suspend fun findById(id: String): HabitStreak?
}
