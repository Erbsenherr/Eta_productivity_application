package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.Note
import com.example.eta.domain.model.SleepLog
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** The two tables of step 43 that stand beside the plan: the sleep log and the notes. */
@Dao
interface TrackingDao {

    @Upsert
    suspend fun upsert(log: SleepLog)

    @Query("SELECT * FROM sleep_logs WHERE date = :date")
    suspend fun findSleepLog(date: LocalDate): SleepLog?

    @Query("SELECT * FROM sleep_logs WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeSleepLogs(from: LocalDate, to: LocalDate): Flow<List<SleepLog>>

    @Query("SELECT * FROM sleep_logs WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun findSleepLogs(from: LocalDate, to: LocalDate): List<SleepLog>

    @Upsert
    suspend fun upsert(note: Note)

    @Query("SELECT * FROM notes")
    fun observeNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findNote(id: String): Note?

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: String)
}
