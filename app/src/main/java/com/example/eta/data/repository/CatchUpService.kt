package com.example.eta.data.repository

import com.example.eta.data.local.DayPlanDao
import com.example.eta.data.local.PlannedBlockDao
import com.example.eta.domain.reevaluation.DailySettlement
import com.example.eta.domain.streak.CATCH_UP_PHRASE
import com.example.eta.domain.streak.CATCH_UP_WINDOW_DAYS
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/** What happened when a past day was made up. */
sealed interface CatchUpResult {
    data class Resolved(val date: LocalDate, val settlement: DailySettlement) : CatchUpResult
    data class Refused(val reason: String) : CatchUpResult
}

/**
 * Making up a day that the evening never settled.
 *
 * Deliberately hard to reach. A skipped day costs the streak and its points, and
 * that consequence is the mechanic — so this exists only for the case the user
 * named: the app or the phone got in the way. The phrase has to be typed out in
 * full, which is friction on purpose rather than a checkbox to click past.
 *
 * **Contracts are not paid retroactively.** A contract is a promise attested to
 * on the day; saying a week later that it was kept is a different act, and one
 * this service is in no position to witness. The harvest of what was actually
 * ticked off is another matter — that record exists, and it is paid.
 */
class CatchUpService(
    private val dayPlanDao: DayPlanDao,
    private val blockDao: PlannedBlockDao,
    private val reevaluationService: ReevaluationService,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    /**
     * Past days the app planned but never settled, newest first.
     *
     * Bounded by the window: a day a month gone cannot meaningfully be recalled,
     * and offering it would only invite guessing.
     */
    suspend fun openDays(): List<LocalDate> {
        val today = clock.todayIn(timeZone)
        val from = today.minus(DatePeriod(days = CATCH_UP_WINDOW_DAYS))
        val yesterday = today.minus(DatePeriod(days = 1))

        val settled = dayPlanDao.findSettledDates(from, yesterday).toSet()
        return blockDao.findDatesWithBlocks(from, yesterday)
            .filterNot { it in settled }
            .sortedDescending()
    }

    /** Settles [date] after the fact, if the phrase was typed exactly. */
    suspend fun catchUp(date: LocalDate, phrase: String): CatchUpResult {
        if (phrase.trim() != CATCH_UP_PHRASE) {
            return CatchUpResult.Refused("Die Eingabe stimmt nicht.")
        }
        val today = clock.todayIn(timeZone)
        if (date >= today) {
            return CatchUpResult.Refused("Heute wird über den Tagesabschluss abgerechnet.")
        }
        if (date < today.minus(DatePeriod(days = CATCH_UP_WINDOW_DAYS))) {
            return CatchUpResult.Refused("Der Tag liegt zu lange zurück.")
        }
        if (dayPlanDao.find(date)?.isSettled == true) {
            return CatchUpResult.Refused("Der Tag ist schon abgerechnet.")
        }

        // No verdicts: contracts are not credited after the fact. The journal keeps
        // the reason, so a made-up day is never indistinguishable from a lived one.
        val settlement = reevaluationService.settle(
            date = date,
            verdicts = emptyList(),
            journal = mapOf(CATCH_UP_PHRASE to "Nachgetragen am $today."),
        )
        return CatchUpResult.Resolved(date, settlement)
    }
}
