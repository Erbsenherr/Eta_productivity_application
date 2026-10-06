package com.example.eta.domain.contract

import kotlin.math.roundToInt

/** One point of a drawn signature, in a unit square: 0..1 in both directions. */
data class SignaturePoint(val x: Float, val y: Float)

/**
 * A signature drawn with a finger, as strokes of points.
 *
 * Stored in the **existing** `contracts.signature` TEXT column — encoded to one
 * string the way `RecurrenceRule` and the setup's answers are — so the database
 * stays at version 18 and a signature travels with an ordinary backup. The
 * alternative, a bitmap in a BLOB, would have cost a migration and a far bigger
 * row for a picture of the same three curves.
 *
 * Coordinates are normalized, so the same signature is drawn correctly whatever
 * size the view has: the pad divides by its own width and height, and the view
 * multiplies by its own.
 *
 * A contract signed before this existed holds a typed name in that column. Decoding
 * fails on it and returns null, which is exactly what the screen needs to fall back
 * to printing it — nothing has to be converted.
 */
data class Signature(val strokes: List<List<SignaturePoint>>) {

    /** Whether there is anything to see. An empty pad is not a signature. */
    val isDrawn: Boolean get() = strokes.any { it.isNotEmpty() }

    /**
     * `x,y` per point, points separated by spaces, strokes by semicolons.
     *
     * Quantized to thousandths and written as integers: three decimals are finer
     * than a fingertip and keep the string a fraction of the length a float
     * rendering would take.
     */
    fun encode(): String = strokes
        .filter { it.isNotEmpty() }
        .joinToString(";") { stroke ->
            stroke.joinToString(" ") { point ->
                "${(point.x * SCALE).roundToInt()},${(point.y * SCALE).roundToInt()}"
            }
        }

    companion object {
        private const val SCALE = 1000f

        /**
         * The most points one signature keeps.
         *
         * A slow finger can produce thousands, and every one of them ends up in a
         * database row and in every backup of it. Whatever is over is dropped from
         * the end, which loses the tail of a scribble rather than the shape of it.
         */
        const val MAX_POINTS = 2000

        fun decode(text: String): Signature? {
            if (text.isBlank()) return null
            val strokes = text.split(";").map { stroke ->
                stroke.trim().split(" ").filter { it.isNotBlank() }.map { pair ->
                    val parts = pair.split(",")
                    if (parts.size != 2) return null
                    val x = parts[0].toIntOrNull() ?: return null
                    val y = parts[1].toIntOrNull() ?: return null
                    SignaturePoint(x / SCALE, y / SCALE)
                }
            }
            val signature = Signature(strokes.filter { it.isNotEmpty() })
            return signature.takeIf { it.isDrawn }
        }
    }
}
