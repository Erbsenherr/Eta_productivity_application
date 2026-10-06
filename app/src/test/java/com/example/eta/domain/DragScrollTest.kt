package com.example.eta.domain

import com.example.eta.domain.planning.AUTO_SCROLL_MAX_STEP
import com.example.eta.domain.planning.autoScrollStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dragging a block past the edge of the screen, and how fast the day follows. */
class DragScrollTest {

    private val top = 200f
    private val height = 1000f
    private val edge = 120f

    private fun step(pointerY: Float) =
        autoScrollStep(pointerY, top, height, edge, AUTO_SCROLL_MAX_STEP)

    @Test
    fun `the middle of the screen scrolls nothing`() {
        assertEquals(0f, step(top + height / 2f), 0.001f)
    }

    @Test
    fun `the top band scrolls back, the bottom band forward`() {
        assertTrue(step(top + 10f) < 0f)
        assertTrue(step(top + height - 10f) > 0f)
    }

    @Test
    fun `the speed ramps rather than switching on`() {
        // Just inside the band it is a crawl; at the edge it is the full step.
        val shallow = step(top + edge - 1f)
        val deep = step(top + 1f)

        assertTrue(kotlin.math.abs(shallow) < kotlin.math.abs(deep))
        assertTrue(kotlin.math.abs(shallow) < AUTO_SCROLL_MAX_STEP / 4f)
    }

    @Test
    fun `at the very edge and beyond it the step is the maximum`() {
        assertEquals(-AUTO_SCROLL_MAX_STEP, step(top), 0.001f)
        assertEquals(-AUTO_SCROLL_MAX_STEP, step(top - 500f), 0.001f)
        assertEquals(AUTO_SCROLL_MAX_STEP, step(top + height), 0.001f)
        assertEquals(AUTO_SCROLL_MAX_STEP, step(top + height + 500f), 0.001f)
    }

    @Test
    fun `a viewport shorter than two bands does not scroll both ways at once`() {
        // The bands would overlap; the middle has to stay a dead zone or a finger
        // sitting still would be told to go up and down in the same frame.
        val shortHeight = 100f
        val middle = top + shortHeight / 2f

        assertEquals(0f, autoScrollStep(middle, top, shortHeight, edge), 0.001f)
        assertTrue(autoScrollStep(top + 5f, top, shortHeight, edge) < 0f)
        assertTrue(autoScrollStep(top + shortHeight - 5f, top, shortHeight, edge) > 0f)
    }

    @Test
    fun `a viewport with no height is never scrolled`() {
        assertEquals(0f, autoScrollStep(500f, top, 0f, edge), 0.001f)
    }
}
