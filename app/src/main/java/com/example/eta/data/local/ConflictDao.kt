package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.ConflictDismissal
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface ConflictDao {

    @Upsert
    suspend fun upsert(dismissal: ConflictDismissal)

    /** What the dashboard has been told to keep quiet about, from [from] on. */
    @Query("SELECT * FROM conflict_dismissals WHERE date >= :from")
    fun observeFrom(from: LocalDate): Flow<List<ConflictDismissal>>

    /** Yesterday's answers are about blocks nobody will see again. */
    @Query("DELETE FROM conflict_dismissals WHERE date < :before")
    suspend fun deleteBefore(before: LocalDate)
}
