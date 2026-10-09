package com.example.eta.domain.tutorial

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn

/** The hour the tutorial's day is entered at: mid-morning, in the middle of work. */
val TUTORIAL_MORNING = LocalTime(10, 30)

/** The hour it jumps to for the Tagesabschluss. */
val TUTORIAL_EVENING = LocalTime(20, 0)

/**
 * The clock of the tutorial's simulated day.
 *
 * It runs — a second is a second — but from an hour of its own choosing, so the
 * example day reads the same whenever the tutorial is opened: "Gerade" is the
 * work block at half past ten in the morning, at midnight as well as at noon.
 * Laying the examples around the real hour instead would have needed a different
 * day for every hour, and none at all for the last one before midnight.
 *
 * The **date** stays the real one the tutorial was started on, and is fixed: a
 * tutorial begun at 23:58 does not roll over into the next day under the user.
 */
class TutorialClock(
    private val real: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    startAt: LocalTime = TUTORIAL_MORNING,
) : Clock {

    val today: LocalDate = real.todayIn(timeZone)

    @Volatile
    private var offset: Duration = Duration.ZERO

    init {
        jumpTo(startAt)
    }

    /** Sets the simulated day to [time]; it goes on running from there. */
    fun jumpTo(time: LocalTime) {
        offset = LocalDateTime(today, time).toInstant(timeZone) - real.now()
    }

    override fun now(): Instant = real.now() + offset
}
