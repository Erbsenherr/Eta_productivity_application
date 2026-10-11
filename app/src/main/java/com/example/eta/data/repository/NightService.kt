package com.example.eta.data.repository

import com.example.eta.data.local.PlannerExtrasDao
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.NightOverride
import com.example.eta.domain.model.PlannerDismissal
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.PlannerWarning
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.isOwnedBySettings
import com.example.eta.domain.setup.movedTo
import com.example.eta.domain.setup.nightEndingOn
import com.example.eta.domain.setup.withNightEndingOn
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * Changing a night by hand — the long press on the shaded hours.
 *
 * Two different acts behind one window. From the **day planner** it is this
 * night and no other ([overrideNight]): a row in `night_overrides`, and the two
 * blocks that hang off the night moved along on their day. From the **week
 * timetable** it is the standing answer for that weekday ([saveWeekdayNight]),
 * which goes through the settings' own save so the two tasks are regenerated.
 *
 * It also keeps the planner's waved-away warnings, which are the other thing a
 * single day carries besides its blocks.
 */
class NightService(
    private val dao: PlannerExtrasDao,
    private val setupRepository: SetupRepository,
    private val planRepository: PlanRepository,
    private val clock: Clock = Clock.System,
) {

    /** Every night that has hours of its own, by the date it ends on. */
    fun observeOverrides(): Flow<Map<LocalDate, NightOverride>> =
        dao.observeOverrides().map { rows -> rows.associateBy { it.date } }

    suspend fun overrides(): Map<LocalDate, NightOverride> = dao.overrides().associateBy { it.date }

    /**
     * Gives the night that ends on [date] hours of its own.
     *
     * Hours that are the standing ones again take the override away rather than
     * storing a copy of the rule: a night that only *looks* overridden would
     * stop following the settings the next time they change.
     *
     * The morning routine of that day then starts at the new hour of getting up,
     * and winding down ends at the new bedtime — both keep their length. Only
     * while they are still open, and winding down only while it stays on the
     * calendar day its block stands on: a block cannot change its date without
     * the standing schedule laying the old one down again.
     */
    suspend fun overrideNight(date: LocalDate, sleep: LocalTime, wake: LocalTime) {
        val setup = setupRepository.find() ?: return
        val usual = setup.nightEndingOn(date.dayOfWeek)
        val night = usual.movedTo(sleep, wake)
        if (night == usual) {
            dao.deleteOverride(date)
        } else {
            dao.upsert(NightOverride(date = date, sleep = sleep, wake = wake, updatedAt = clock.now()))
        }
        moveNightBlocks(date, usual, night)
    }

    /** Back to the standing hours for the night that ends on [date]. */
    suspend fun clearOverride(date: LocalDate) {
        val setup = setupRepository.find() ?: return
        dao.deleteOverride(date)
        val usual = setup.nightEndingOn(date.dayOfWeek)
        moveNightBlocks(date, usual, usual)
    }

    private suspend fun moveNightBlocks(date: LocalDate, usual: NightTimes, night: NightTimes) {
        val now = clock.now()
        planRepository.findDay(date)
            .filter { it.item.role == ItemRole.MORNING && isOwnedBySettings(it.item.id) && it.block.isOpen }
            .forEach { planRepository.addBlock(it.block.copy(start = night.wake, updatedAt = now)) }

        val usualDay = Math.floorDiv(usual.bedPrepOffset(), MINUTES_PER_DAY)
        if (Math.floorDiv(night.bedPrepOffset(), MINUTES_PER_DAY) != usualDay) return
        val wakeDay = date.dayOfWeek.name.lowercase()
        planRepository.findDay(date.plus(DatePeriod(days = usualDay)))
            .filter { it.item.role == ItemRole.BED_PREP && isOwnedBySettings(it.item.id) && it.block.isOpen }
            // With nights of their own there is one definition per night, named
            // after the day it ends on; with every night alike there is the one.
            .filter { !it.item.id.contains('-') || it.item.id.endsWith("-$wakeDay") }
            .forEach { planRepository.addBlock(it.block.copy(start = night.bedPrep, updatedAt = now)) }
    }

    /**
     * Changes the standing night that ends on [day], and that weekday's alone.
     *
     * Which puts the setup on "Für jeden Wochentag selbst entscheiden" if it was
     * not there already — the user's rule for the week timetable. Saved through
     * the settings' path, so Bettfertig machen and the Morgenroutine follow, with
     * today and a confirmed tomorrow left as they are.
     */
    suspend fun saveWeekdayNight(day: DayOfWeek, sleep: LocalTime, wake: LocalTime) {
        val setup = setupRepository.find() ?: return
        val night = setup.nightEndingOn(day).movedTo(sleep, wake)
        if (night == setup.nightEndingOn(day)) return
        setupRepository.saveSettings(setup.withNightEndingOn(day, night))
    }

    fun observeDismissed(date: LocalDate): Flow<Set<PlannerWarning>> =
        dao.observeDismissed(date).map { kinds ->
            kinds.mapNotNull { name -> PlannerWarning.entries.firstOrNull { it.name == name } }.toSet()
        }

    suspend fun dismiss(date: LocalDate, warning: PlannerWarning) {
        dao.upsert(PlannerDismissal(date = date, kind = warning.name, dismissedAt = clock.now()))
    }
}
