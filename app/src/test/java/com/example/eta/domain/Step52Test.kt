package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Reward
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.UnplannedTone
import com.example.eta.domain.planning.unplannedMinutes
import com.example.eta.domain.planning.unplannedTone
import com.example.eta.domain.reward.Earning
import com.example.eta.domain.reward.RewardRole
import com.example.eta.domain.reward.RewardTarget
import com.example.eta.domain.reward.pourInto
import com.example.eta.domain.reward.rolesOf
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.SetupLabels
import com.example.eta.domain.setup.SetupRoutine
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.isOwnedBySettings
import com.example.eta.domain.setup.recurringItems
import com.example.eta.domain.sound.GroupAudio
import com.example.eta.domain.sound.SoundEvent
import com.example.eta.domain.sound.SoundGroup
import com.example.eta.domain.sound.group
import com.example.eta.domain.sound.mayPlay
import com.example.eta.domain.sound.playbackVolume
import com.example.eta.domain.sound.streamStep
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 52: sounds in two groups with their own quiet and volume, the hours a
 * day leaves unplanned, one-off rewards, the Termin, and the evening routine.
 */
class Step52Test {

    private val now = Instant.parse("2026-10-25T08:00:00Z")
    private val date = LocalDate(2026, 10, 25)

    // --- sounds ---------------------------------------------------------------

    @Test
    fun `the wake alarm and the planning phases are alarms, everything else a notification`() {
        assertEquals(SoundGroup.ALARM, SoundEvent.WAKE.group)
        assertEquals(SoundGroup.ALARM, SoundEvent.PLANNING.group)
        SoundEvent.entries.filter { it != SoundEvent.WAKE && it != SoundEvent.PLANNING }
            .forEach { assertEquals(it.name, SoundGroup.NOTIFICATION, it.group) }
    }

    @Test
    fun `every combination of the two quiet switches does what it says`() {
        val none = GroupAudio()
        val dnd = GroupAudio(throughDoNotDisturb = true)
        val silent = GroupAudio(throughSilentRinger = true)
        val both = GroupAudio(throughDoNotDisturb = true, throughSilentRinger = true)

        // Nothing asked for: everything plays.
        listOf(none, dnd, silent, both).forEach { assertTrue(mayPlay(it, doNotDisturb = false, ringerMuted = false)) }
        // Do-Not-Disturb: its switch alone, whatever the ringer reports under it.
        assertFalse(mayPlay(none, doNotDisturb = true, ringerMuted = true))
        assertFalse(mayPlay(silent, doNotDisturb = true, ringerMuted = true))
        assertTrue(mayPlay(dnd, doNotDisturb = true, ringerMuted = true))
        assertTrue(mayPlay(both, doNotDisturb = true, ringerMuted = false))
        // A muted ringer without DND: the other switch.
        assertFalse(mayPlay(none, doNotDisturb = false, ringerMuted = true))
        assertFalse(mayPlay(dnd, doNotDisturb = false, ringerMuted = true))
        assertTrue(mayPlay(silent, doNotDisturb = false, ringerMuted = true))
        assertTrue(mayPlay(both, doNotDisturb = false, ringerMuted = true))
    }

    @Test
    fun `a constant volume ignores the sliders, bound follows the right one`() {
        val constant = GroupAudio(constantVolume = 40)
        assertEquals(0.4f, playbackVolume(SoundGroup.NOTIFICATION, constant, notificationLevel = 0f)!!, 1e-6f)
        assertEquals(0.4f, playbackVolume(SoundGroup.ALARM, constant, notificationLevel = 0f)!!, 1e-6f)

        val bound = GroupAudio()
        // An alarm keeps the alarm slider; a notification takes the notification one's level.
        assertNull(playbackVolume(SoundGroup.ALARM, bound, notificationLevel = 0f))
        assertEquals(0f, playbackVolume(SoundGroup.NOTIFICATION, bound, notificationLevel = 0f)!!, 1e-6f)
        assertEquals(0.5f, playbackVolume(SoundGroup.NOTIFICATION, bound, notificationLevel = 0.5f)!!, 1e-6f)
    }

    @Test
    fun `anything audible is at least one step of the stream`() {
        assertEquals(0, streamStep(0f, 7))
        assertEquals(1, streamStep(0.05f, 7))
        assertEquals(7, streamStep(1f, 7))
        assertEquals(4, streamStep(0.6f, 7))
    }

    // --- unplanned hours ----------------------------------------------------------

    private fun entry(
        name: String,
        start: LocalTime,
        duration: Duration,
        discarded: Boolean = false,
        completed: Boolean = false,
    ) = BlockWithItem(
        block = PlannedBlock(
            id = "block-$name",
            itemId = "item-$name",
            date = date,
            start = start,
            plannedDuration = duration,
            origin = BlockOrigin.DRAGGED,
            completedAt = now.takeIf { completed },
            discardedAt = now.takeIf { discarded },
            createdAt = now,
            updatedAt = now,
        ),
        item = Item(
            id = "item-$name",
            type = ItemType.TODO,
            name = name,
            stage = Stage.DAY,
            createdAt = now,
            updatedAt = now,
        ),
    )

    @Test
    fun `unplanned is the waking day less what stands on it, overlaps once`() {
        // Night 23:00–07:00: 8 hours asleep, 16 awake.
        val sleep = listOf(0 until 7 * 60, 23 * 60 until 24 * 60)
        val blocks = listOf(
            entry("A", LocalTime(8, 0), 4.hours),
            entry("B", LocalTime(10, 0), 4.hours), // overlaps A by two hours
            entry("Done", LocalTime(15, 0), 1.hours, completed = true),
            entry("Off", LocalTime(17, 0), 3.hours, discarded = true),
        )
        // 16 h awake − (08–14 = 6 h) − 1 h done = 9 h; the cancelled block covers nothing.
        assertEquals(9 * 60, unplannedMinutes(blocks, sleep))
        assertEquals(16 * 60, unplannedMinutes(emptyList(), sleep))
    }

    @Test
    fun `red past two hours, green up to them, grey on a free weekend`() {
        assertEquals(UnplannedTone.OVER, unplannedTone(121, weekend = false, weekendPlanning = false))
        assertEquals(UnplannedTone.WITHIN, unplannedTone(120, weekend = false, weekendPlanning = false))
        assertEquals(UnplannedTone.WITHIN, unplannedTone(0, weekend = false, weekendPlanning = false))
        assertEquals(UnplannedTone.RELAXED, unplannedTone(600, weekend = true, weekendPlanning = false))
        assertEquals(UnplannedTone.OVER, unplannedTone(600, weekend = true, weekendPlanning = true))
    }

    // --- one-off rewards ------------------------------------------------------------

    private fun reward(id: String, cost: Double = 10.0, once: Boolean = false, achieved: Boolean = false) = Reward(
        id = id,
        name = id,
        cost = if (once) 0.0 else cost,
        position = 1,
        once = once,
        condition = "Klausur bestanden".takeIf { once },
        dueAt = now.takeIf { once },
        achievedAt = now.takeIf { achieved },
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `a one-off reward takes no points and lets them pass to the next`() {
        val once = reward("Kino", once = true)
        val daily = reward("Eis", cost = 10.0)
        val gains = pourInto(
            listOf(RewardTarget(once), RewardTarget(daily)),
            listOf(Earning("lernen", standing = false, points = 3.0)),
        )
        assertEquals(listOf("Eis"), gains.map { it.reward.id })
        assertEquals(3.0, gains.single().added, 1e-9)
    }

    @Test
    fun `a one-off is full only once reached, and waits for its goal until then`() {
        val open = reward("Kino", once = true)
        val reached = reward("Kino", once = true, achieved = true)
        assertFalse(open.isFull)
        assertFalse(open.isFilling)
        assertTrue(reached.isFull)
        assertEquals(0f, open.fraction, 0f)
        assertEquals(1f, reached.fraction, 0f)

        val roles = rolesOf(listOf(RewardTarget(open), RewardTarget(reward("Eis"))))
        assertEquals(RewardRole.GOAL, roles["Kino"])
        assertEquals(RewardRole.FILLING, roles["Eis"])
    }

    @Test
    fun `its date asks once it has come, and not after an answer`() {
        val open = reward("Kino", once = true)
        assertTrue(open.isDue(now))
        assertFalse(open.isDue(now - 1.minutes))
        assertFalse(open.copy(achievedAt = now).isDue(now))
        assertFalse(open.copy(expiredAt = now).isDue(now))
        assertTrue(open.copy(expiredAt = now).isPast)
        assertFalse(open.copy(dueAt = null).isDue(now))
    }

    // --- Termin -------------------------------------------------------------------

    @Test
    fun `a Termin stays where it was put and is not taken back to a list`() {
        val block = entry("Arzt", LocalTime(9, 0), 1.hours).block.copy(origin = BlockOrigin.APPOINTMENT)
        assertFalse(block.isMovable)
        assertFalse(block.isHandPlaced)
    }

    // --- the evening routine ---------------------------------------------------------

    @Test
    fun `the old Bettfertig machen is the Abendroutine, a routine owned by the settings`() {
        val items = UserSetup.draft(now).recurringItems(now)
        val evening = items.single { SetupRoutine.EVENING.owns(it.id) }
        assertEquals(SetupLabels.BED_PREP, evening.name)
        assertEquals("Abendroutine", evening.name)
        assertTrue(evening.routineMode)
        assertTrue(isOwnedBySettings(evening.id))
        assertEquals(SetupRoutine.EVENING, SetupRoutine.of(evening.id))
        assertNull(SetupRoutine.of("setup:freetime"))
    }

    @Test
    fun `with a weekend night every evening is one`() {
        val setup = UserSetup.draft(now).copy(
            weekendNight = NightTimes(LocalTime(23, 30), LocalTime(0, 30), LocalTime(9, 0)),
        )
        val evenings = setup.recurringItems(now).filter { SetupRoutine.EVENING.owns(it.id) }
        assertEquals(7, evenings.size)
        assertTrue(evenings.all { it.routineMode && it.name == "Abendroutine" })
    }
}
