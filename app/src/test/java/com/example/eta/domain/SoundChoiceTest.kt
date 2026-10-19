package com.example.eta.domain

import com.example.eta.domain.sound.EtaSoundFile
import com.example.eta.domain.sound.SoundChoice
import com.example.eta.domain.sound.SoundEvent
import com.example.eta.domain.sound.decodeSoundChoice
import com.example.eta.domain.sound.encode
import com.example.eta.domain.sound.etaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A sound chosen per moment: how it is kept, and what it plays. */
class SoundChoiceTest {

    @Test
    fun `every choice survives being stored`() {
        val choices = listOf(
            SoundChoice.Default,
            SoundChoice.Eta(EtaSoundFile.TASK_END),
            SoundChoice.System("content://media/internal/audio/media/42"),
        )
        choices.forEach { assertEquals(it, decodeSoundChoice(it.encode())) }
    }

    @Test
    fun `the default is stored as nothing`() {
        assertNull(SoundChoice.Default.encode())
    }

    @Test
    fun `what cannot be read is the default, never silence`() {
        assertEquals(SoundChoice.Default, decodeSoundChoice(null))
        assertEquals(SoundChoice.Default, decodeSoundChoice(""))
        assertEquals(SoundChoice.Default, decodeSoundChoice("eta:NO_SUCH_FILE"))
    }

    @Test
    fun `the default plays what each moment had before`() {
        assertEquals(EtaSoundFile.TASK_START, SoundChoice.Default.etaFile(SoundEvent.TASK_START))
        assertEquals(EtaSoundFile.POM_PAUSE_START, SoundChoice.Default.etaFile(SoundEvent.TIMER_DONE))
        // The phone's own alarm tone.
        assertNull(SoundChoice.Default.etaFile(SoundEvent.WAKE))
    }

    @Test
    fun `a chosen file overrides the default, a system sound has none`() {
        assertEquals(
            EtaSoundFile.NOTIFICATION,
            SoundChoice.Eta(EtaSoundFile.NOTIFICATION).etaFile(SoundEvent.TASK_START),
        )
        assertNull(SoundChoice.System("content://x").etaFile(SoundEvent.TASK_START))
    }
}
