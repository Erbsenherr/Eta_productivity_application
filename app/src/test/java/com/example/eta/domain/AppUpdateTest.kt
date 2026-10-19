package com.example.eta.domain

import com.example.eta.domain.update.ReleaseAsset
import com.example.eta.domain.update.apkOf
import com.example.eta.domain.update.formatMegabytes
import com.example.eta.domain.update.isNewer
import com.example.eta.domain.update.plainNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The update from GitHub: which release counts as newer, and what is shown of it. */
class AppUpdateTest {

    @Test
    fun `a later stamp is newer, the same or an earlier one is not`() {
        assertTrue(isNewer("2026-10-19-1412", "2026-10-19-1411"))
        assertTrue(isNewer("2027-01-01-0000", "2026-12-31-2359"))
        assertFalse(isNewer("2026-10-19-1412", "2026-10-19-1412"))
        assertFalse(isNewer("2026-10-18-2359", "2026-10-19-0000"))
    }

    @Test
    fun `a build without a stamp is older than every release`() {
        assertTrue(isNewer("2026-10-19-1412", "1.0"))
        assertTrue(isNewer("2026-10-19-1412", ""))
    }

    @Test
    fun `a tag that is not a stamp is never offered`() {
        assertFalse(isNewer("v1.0", "1.0"))
        assertFalse(isNewer("2026-10-19", "2026-01-01-0000"))
    }

    @Test
    fun `the apk is the attachment ending in apk`() {
        val apk = ReleaseAsset("eta-release-2026-10-19-1412.apk", "https://x/apk", 10)
        assertEquals(apk, apkOf(listOf(ReleaseAsset("notes.md", "https://x/md", 1), apk)))
        assertNull(apkOf(listOf(ReleaseAsset("eta.apk.idsig", "https://x/idsig", 1))))
    }

    @Test
    fun `the notes lose their markdown`() {
        val notes = "# Eta – 19.10.\n\n\n## Neu\n- **Updates** in den `Einstellungen`\n  - eingerückt"
        assertEquals(
            "Eta – 19.10.\n\nNeu\n• Updates in den Einstellungen\n  • eingerückt",
            plainNotes(notes),
        )
    }

    @Test
    fun `sizes are megabytes with one decimal`() {
        assertEquals("9,8 MB", formatMegabytes(10_236_980))
        assertEquals("0,0 MB", formatMegabytes(0))
    }
}
