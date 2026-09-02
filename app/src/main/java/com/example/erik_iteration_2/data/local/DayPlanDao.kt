package com.example.erik_iteration_2.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.erik_iteration_2.domain.model.DayPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface DayPlanDao {

    @Upsert
    suspend fun upsert(dayPlan: DayPlan)

    @Query("SELECT * FROM day_plans WHERE date = :date")
    fun observe(date: LocalDate): Flow<DayPlan?>

    @Query("SELECT * FROM day_plans WHERE date = :date")
    suspend fun find(date: LocalDate): DayPlan?

    /** Every day the evening has settled — the raw material of the streak. */
    @Query("SELECT date FROM day_plans WHERE settledAt IS NOT NULL")
    fun observeSettledDates(): Flow<List<LocalDate>>

    @Query("SELECT date FROM day_plans WHERE settledAt IS NOT NULL AND date BETWEEN :from AND :to")
    suspend fun findSettledDates(from: LocalDate, to: LocalDate): List<LocalDate>
}
