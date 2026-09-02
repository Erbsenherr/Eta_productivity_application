package com.example.erik_iteration_2.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.example.erik_iteration_2.domain.model.JournalEntry
import com.example.erik_iteration_2.domain.model.JournalKind
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface JournalDao {

    @Insert
    suspend fun insertAll(entries: List<JournalEntry>)

    @Query("SELECT * FROM journal_entries WHERE kind = :kind ORDER BY date DESC, createdAt DESC")
    fun observeByKind(kind: JournalKind): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE date = :date AND kind = :kind")
    suspend fun findForDate(date: LocalDate, kind: JournalKind): List<JournalEntry>
}
