package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.Contract
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

    /**
     * Everything the evening still has to ask about.
     *
     * `PROBATION` is in the list: a contract being served out after a breach is
     * asked exactly like the others, and only the payout is different. Leaving it
     * out would make "weiterführen" mean nothing at all.
     */
    @Query("SELECT * FROM contracts WHERE state IN ('ACTIVE', 'LEGACY', 'PROBATION') ORDER BY slot")
    suspend fun findRunning(): List<Contract>

    @Query("SELECT * FROM contracts")
    suspend fun findAll(): List<Contract>

    @Query("SELECT * FROM contracts WHERE id = :id")
    suspend fun findById(id: String): Contract?
}
