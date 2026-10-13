package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import java.util.UUID

/**
 * A note in the Notiz-Merger: a title and a text, and nothing the plan knows of.
 *
 * Its own table rather than an [Item] in some stage of its own: a note has no
 * one-month clock, no Sperrliste and no place in any list, and every query that
 * reads items would have had to learn to skip it. "Taskify" is what carries one
 * across — it makes a ToDo out of it and stamps [taskifiedAt], and the note
 * itself stays.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val text: String,
    /** When a ToDo was last made of this note; null if none ever was. */
    val taskifiedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
