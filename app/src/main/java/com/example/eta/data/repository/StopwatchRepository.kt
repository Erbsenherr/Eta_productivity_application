package com.example.eta.data.repository

import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.local.StopwatchDao
import com.example.eta.domain.model.StopwatchTime
import com.example.eta.domain.stats.ended
import com.example.eta.domain.stats.paused
import com.example.eta.domain.stats.resumed
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/** The stopwatch of the "Gerade" box and its times (step 56). */
class StopwatchRepository(
    private val dao: StopwatchDao,
    private val clock: Clock = Clock.System,
) {

    fun observeRunning(): Flow<List<StopwatchTime>> = dao.observeRunning()

    fun observeEnded(): Flow<List<StopwatchTime>> = dao.observeEnded()

    /**
     * Starts one for [entry], counting from [from] — now, or the task's start
     * by the plan. Not a second one for a block that has one going.
     */
    suspend fun start(entry: BlockWithItem, from: Instant, running: List<StopwatchTime>) {
        if (running.any { it.blockId == entry.block.id }) return
        val now = clock.now()
        dao.upsert(
            StopwatchTime(
                itemId = entry.item.id,
                blockId = entry.block.id,
                name = entry.item.name,
                date = entry.block.date,
                runningSince = minOf(from, now),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun pause(id: String) = change(id) { it.paused(clock.now()) }

    suspend fun resume(id: String) = change(id) { it.resumed(clock.now()) }

    suspend fun end(id: String) = change(id) { it.ended(clock.now()) }

    /** A time corrected by hand in the statistics. */
    suspend fun correct(id: String, worked: Duration) =
        change(id) { it.copy(worked = worked.coerceAtLeast(Duration.ZERO), updatedAt = clock.now()) }

    suspend fun delete(id: String) = dao.delete(id)

    private suspend fun change(id: String, change: (StopwatchTime) -> StopwatchTime) {
        val time = dao.find(id) ?: return
        val changed = change(time)
        if (changed != time) dao.upsert(changed)
    }
}
