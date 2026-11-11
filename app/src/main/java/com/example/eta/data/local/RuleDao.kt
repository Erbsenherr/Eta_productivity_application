package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.Rule
import com.example.eta.domain.model.RuleBreach
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.Flow

/** The Regelwerk's rules (step 53). */
@Dao
interface RuleDao {

    @Upsert
    suspend fun upsert(rule: Rule)

    @Query("SELECT * FROM rules ORDER BY position, createdAt")
    fun observeAll(): Flow<List<Rule>>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun find(id: String): Rule?

    @Query("SELECT COALESCE(MAX(position), -1) FROM rules")
    suspend fun lastPosition(): Int

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: String)

    // --- breaches (step 57) ---

    @Upsert
    suspend fun upsertBreach(breach: RuleBreach)

    @Query("SELECT * FROM rule_breaches WHERE id = :id")
    suspend fun findBreach(id: String): RuleBreach?

    @Query("SELECT * FROM rule_breaches ORDER BY date DESC, createdAt DESC")
    fun observeBreaches(): Flow<List<RuleBreach>>

    @Query("SELECT * FROM rule_breaches WHERE date = :date")
    suspend fun breachesOn(date: LocalDate): List<RuleBreach>

    @Query("DELETE FROM rule_breaches WHERE id = :id")
    suspend fun deleteBreach(id: String)

    @Query("DELETE FROM rule_breaches WHERE ruleId = :ruleId")
    suspend fun deleteBreachesOf(ruleId: String)
}
