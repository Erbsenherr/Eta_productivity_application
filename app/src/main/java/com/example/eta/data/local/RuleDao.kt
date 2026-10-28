package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.Rule
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
}
