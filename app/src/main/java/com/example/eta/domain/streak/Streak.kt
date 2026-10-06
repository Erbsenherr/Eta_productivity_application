package com.example.eta.domain.streak

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** How far back a day may still be made up after a technical failure. */
const val CATCH_UP_WINDOW_DAYS = 30

/** The phrase the settings field demands before a past day can be resolved. */
const val CATCH_UP_PHRASE = "TECHNISCHE SCHWIERIGKEITEN"

/** The run of settled days, and whether today still has to be earned. */
data class Streak(
    val length: Int,
    /** Today is not settled yet — the run is alive but not yet extended. */
    val todayOpen: Boolean,
) {
    val isBroken: Boolean get() = length == 0
}

/**
 * The run of consecutive settled days ending now.
 *
 * Today counts once it is settled, and until then the run is measured from
 * yesterday: a day still being lived has not been skipped, and a streak that
 * showed zero every morning would say nothing.
 *
 * Yesterday unsettled means the run is over — that is the whole point of the
 * mechanic, and the only way back is the catch-up in the settings.
 */
fun streakOf(settled: Set<LocalDate>, today: LocalDate): Streak {
    val todaySettled = today in settled
    var cursor = if (todaySettled) today else today.minus(DatePeriod(days = 1))

    var length = 0
    while (cursor in settled) {
        length++
        cursor = cursor.minus(DatePeriod(days = 1))
    }

    return Streak(length = length, todayOpen = !todaySettled)
}
