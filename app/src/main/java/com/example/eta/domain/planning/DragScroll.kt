package com.example.eta.domain.planning

/**
 * How close to the edge of the viewport a dragging finger has to come before the
 * day starts moving under it, in pixels.
 *
 * Generous rather than tight: the gesture has to be reachable with a thumb that
 * is already covering part of the screen, and a narrow band would mean hunting
 * for it.
 */
const val AUTO_SCROLL_EDGE_PX = 120f

/** The fastest the day moves, in pixels per frame — about four hours a second. */
const val AUTO_SCROLL_MAX_STEP = 4.5f

/**
 * How far to scroll this frame while a drag is under way.
 *
 * Negative moves the day back towards midnight, positive towards midnight the
 * other side; zero means the finger is nowhere near an edge and nothing happens.
 *
 * The speed **ramps with proximity** rather than switching on: at the outer
 * boundary of the band it is a crawl, at the very edge it is [AUTO_SCROLL_MAX_STEP].
 * A constant speed makes the last quarter of an hour before the edge impossible
 * to place, because the day is already running by the time the finger gets there.
 *
 * [pointerY], [viewportTop] and [viewportHeight] are all in the same coordinate
 * space; the screen reads them straight out of layout, so they stay right while
 * the day scrolls.
 */
fun autoScrollStep(
    pointerY: Float,
    viewportTop: Float,
    viewportHeight: Float,
    edge: Float = AUTO_SCROLL_EDGE_PX,
    maxStep: Float = AUTO_SCROLL_MAX_STEP,
): Float {
    if (viewportHeight <= 0f || edge <= 0f) return 0f

    val fromTop = pointerY - viewportTop
    val fromBottom = (viewportTop + viewportHeight) - pointerY

    // A viewport shorter than two bands would have them overlap, and a finger in
    // the middle would be told to scroll both ways at once.
    val band = edge.coerceAtMost(viewportHeight / 2f)

    return when {
        fromTop < band -> -maxStep * ramp(band - fromTop, band)
        fromBottom < band -> maxStep * ramp(band - fromBottom, band)
        else -> 0f
    }
}

/** 0 at the inner boundary of the band, 1 at the edge and beyond it. */
private fun ramp(depth: Float, band: Float): Float = (depth / band).coerceIn(0f, 1f)
