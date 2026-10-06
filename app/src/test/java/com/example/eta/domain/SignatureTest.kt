package com.example.eta.domain

import com.example.eta.domain.contract.Signature
import com.example.eta.domain.contract.SignaturePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawn signature's round trip through the one TEXT column it lives in.
 *
 * Guarded the way `SetupEncodingTest` guards the questionnaire: a broken encoding
 * would quietly turn every signed contract into an unreadable string, and there is
 * no second copy of it anywhere.
 */
class SignatureTest {

    private fun points(vararg pairs: Pair<Float, Float>) =
        pairs.map { SignaturePoint(it.first, it.second) }

    @Test
    fun `strokes survive the round trip to a thousandth`() {
        val signature = Signature(
            listOf(
                points(0f to 0f, 0.5f to 0.25f, 1f to 1f),
                points(0.125f to 0.875f),
            ),
        )

        val back = Signature.decode(signature.encode())

        assertNotNull(back)
        assertEquals(2, back!!.strokes.size)
        assertEquals(3, back.strokes[0].size)
        assertEquals(0.5f, back.strokes[0][1].x, 0.001f)
        assertEquals(0.25f, back.strokes[0][1].y, 0.001f)
        assertEquals(0.875f, back.strokes[1][0].y, 0.001f)
    }

    @Test
    fun `an empty pad is not a signature`() {
        assertFalse(Signature(emptyList()).isDrawn)
        assertFalse(Signature(listOf(emptyList())).isDrawn)
        assertEquals("", Signature(listOf(emptyList())).encode())
        assertNull(Signature.decode(""))
        assertNull(Signature.decode("   "))
    }

    @Test
    fun `a single dot counts`() {
        val dot = Signature(listOf(points(0.4f to 0.6f)))

        assertTrue(dot.isDrawn)
        assertEquals(1, Signature.decode(dot.encode())?.strokes?.first()?.size)
    }

    @Test
    fun `a typed name from before the pad decodes to nothing, so the screen prints it`() {
        assertNull(Signature.decode("Erik Mustermann"))
        assertNull(Signature.decode("12,"))
        assertNull(Signature.decode("a,b"))
    }
}
