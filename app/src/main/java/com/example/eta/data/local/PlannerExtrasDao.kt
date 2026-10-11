package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.NightOverride
import com.example.eta.domain.model.PlannerDismissal
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** What a single day carries besides its blocks: a night of its own, and warnings waved away. */
@Dao
interface PlannerExtrasDao {

    @Upsert
    suspend fun upsert(override: NightOverride)

    @Query("DELETE FROM night_overrides WHERE date = :date")
    suspend fun deleteOverride(date: LocalDate)

    @Query("SELECT * FROM night_overrides")
    fun observeOverrides(): Flow<List<NightOverride>>

    @Query("SELECT * FROM night_overrides")
    suspend fun overrides(): List<NightOverride>

    @Upsert
    suspend fun upsert(dismissal: PlannerDismissal)

    @Query("SELECT kind FROM planner_dismissals WHERE date = :date")
    fun observeDismissed(date: LocalDate): Flow<List<String>>
}
