package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.Reward
import com.example.eta.domain.model.RewardTask
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardDao {

    @Upsert
    suspend fun upsert(reward: Reward)

    @Upsert
    suspend fun upsertAll(rewards: List<Reward>)

    @Delete
    suspend fun delete(reward: Reward)

    @Query("SELECT * FROM rewards ORDER BY position, createdAt")
    fun observeAll(): Flow<List<Reward>>

    @Query("SELECT * FROM rewards ORDER BY position, createdAt")
    suspend fun all(): List<Reward>

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun findById(id: String): Reward?

    @Query("SELECT COALESCE(MAX(position), 0) FROM rewards")
    suspend fun lastPosition(): Int

    @Query("SELECT * FROM reward_tasks")
    fun observeTasks(): Flow<List<RewardTask>>

    @Query("SELECT * FROM reward_tasks")
    suspend fun tasks(): List<RewardTask>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTasks(tasks: List<RewardTask>)

    @Query("DELETE FROM reward_tasks WHERE rewardId = :rewardId")
    suspend fun clearTasks(rewardId: String)

    @Query("DELETE FROM reward_tasks WHERE rewardId = :rewardId AND itemId = :itemId")
    suspend fun deleteTask(rewardId: String, itemId: String)
}
