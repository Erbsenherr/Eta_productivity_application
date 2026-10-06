package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import java.util.UUID

enum class JournalKind {
    /** The self-reflection questions in the evening reevaluation. */
    DAILY,

    /** The weekly evaluation field in the weekly planning phase. */
    WEEKLY,
}

/**
 * One answer, kept.
 *
 * Both documents ask for the same thing in the same words — answers are
 * timestamped and archived — so one table serves both, distinguished by [kind].
 * The question is stored alongside the answer rather than referenced by id: the
 * wording may change, and an archived answer to a question nobody can read any
 * more is worthless.
 */
@Entity(
    tableName = "journal_entries",
    indices = [Index("date"), Index("kind")],
)
data class JournalEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kind: JournalKind,
    /** The day being reflected on, which is not always the day of writing. */
    val date: LocalDate,
    val question: String,
    val answer: String,
    val createdAt: Instant,
)
