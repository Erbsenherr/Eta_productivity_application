package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.Vacation
import com.example.eta.domain.model.VacationRule
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface VacationDao {

    @Upsert
    suspend fun upsert(vacation: Vacation)

    @Upsert
    suspend fun upsertRules(rules: List<VacationRule>)

    @Query("SELECT * FROM vacations ORDER BY `from` DESC")
    fun observeAll(): Flow<List<Vacation>>

    @Query("SELECT * FROM vacation_rules")
    fun observeAllRules(): Flow<List<VacationRule>>

    /** Everything from [date] onwards — what expansion of a future range needs. */
    @Query("SELECT * FROM vacations WHERE `to` >= :date ORDER BY `from`")
    suspend fun findFrom(date: LocalDate): List<Vacation>

    @Query("SELECT * FROM vacation_rules WHERE vacationId = :vacationId")
    suspend fun findRules(vacationId: String): List<VacationRule>

    @Query("DELETE FROM vacation_rules WHERE vacationId = :vacationId")
    suspend fun clearRules(vacationId: String)

    @Query("DELETE FROM vacations WHERE id = :id")
    suspend fun delete(id: String)
}
