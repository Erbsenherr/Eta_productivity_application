package com.example.erik_iteration_2.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.erik_iteration_2.domain.model.Contract
import kotlinx.coroutines.flow.Flow

@Dao
interface ContractDao {

    @Upsert
    suspend fun upsert(contract: Contract)

    @Upsert
    suspend fun upsertAll(contracts: List<Contract>)

    /**
     * Everything, broken ones included: a breach still holds its slot for a month,
     * so the slot view cannot be built from the running contracts alone.
     */
    @Query("SELECT * FROM contracts ORDER BY signedOn DESC")
    fun observeAll(): Flow<List<Contract>>

    @Query("SELECT * FROM contracts WHERE state IN ('ACTIVE', 'LEGACY') ORDER BY slot")
    suspend fun findRunning(): List<Contract>

    @Query("SELECT * FROM contracts")
    suspend fun findAll(): List<Contract>

    @Query("SELECT * FROM contracts WHERE id = :id")
    suspend fun findById(id: String): Contract?
}
