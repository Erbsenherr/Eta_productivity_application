package com.example.eta.domain.stats

import com.example.eta.data.local.BlockWithItem

/**
 * How a stretch went against the user's usual (step 59) — what decides
 * whether the Wochenrückblick and the Tagesabschluss encourage or praise.
 */
enum class Mood { WORSE, USUAL, BETTER }

/**
 * More figures worse than better: [Mood.WORSE]; more better: [Mood.BETTER];
 * as many, or every one as usual: [Mood.USUAL]. Null where nothing could be
 * compared — no history, or nothing counted — and then nothing is said.
 */
fun moodOf(figures: List<ReviewFigure>): Mood? {
    val known = figures.map { it.trend }.filter { it != Trend.UNKNOWN }
    if (known.isEmpty()) return null
    val better = known.count { it == Trend.BETTER }
    val worse = known.count { it == Trend.WORSE }
    return when {
        worse > better -> Mood.WORSE
        better > worse -> Mood.BETTER
        else -> Mood.USUAL
    }
}

private val ENCOURAGING = listOf(
    "Jeder braucht mal eine Pause.",
    "Nicht aufgeben, du schaffst das!",
    "Ein schwächerer Abschnitt sagt nichts über dich aus.",
    "Morgen ist ein neuer Anfang.",
    "Kleine Schritte zählen auch.",
    "Sei nachsichtig mit dir — dass du hier bist, zeigt, dass du dranbleibst.",
)

private val STEADY = listOf(
    "Du hältst dein Niveau — gut gemacht!",
    "Beständigkeit ist eine Stärke. Weiter so!",
    "Solide wie immer. Darauf kannst du bauen.",
    "Du bleibst dran, und das zahlt sich aus.",
)

private val PRAISING = listOf(
    "Besser als sonst — stark!",
    "Klasse, du hast dich gesteigert!",
    "Gut gemacht! Darauf kannst du stolz sein.",
    "Läuft bei dir — weiter so!",
)

/**
 * A short message for [mood]. [seed] picks it — the day's or the week's date,
 * so it stays the same while the page is open and changes from one to the next.
 */
fun affirmation(mood: Mood, seed: Int): String {
    val pool = when (mood) {
        Mood.WORSE -> ENCOURAGING
        Mood.USUAL -> STEADY
        Mood.BETTER -> PRAISING
    }
    return pool[Math.floorMod(seed, pool.size)]
}

/**
 * The Tagesabschluss's comparison (step 59): today's tasks against every day
 * before — the two task figures of the Wochenrückblick, "Aufgaben
 * abgeschlossen" and "Planmäßig erledigt". The sleep figures are left out: one
 * answer a day is a share of one, and says nothing against a usual.
 *
 * Something committed to for later tonight (step 59) is not judged yet.
 */
fun dayReview(today: List<BlockWithItem>, earlier: List<BlockWithItem>): List<ReviewFigure> {
    val judged = today.filterNot { it.block.isOpen && it.block.committedAt != null }
    return listOf(
        ReviewFigure("Aufgaben abgeschlossen", completedShare(judged), completedShare(earlier)),
        ReviewFigure("Planmäßig erledigt", accuracy(judged), accuracy(earlier)),
    )
}

private fun completedShare(blocks: List<BlockWithItem>): Share {
    val tasks = blocks.filter { it.isTakenOnTask() && !it.block.isExcused }
    return Share(tasks.count { it.block.isCompleted }, tasks.size)
}
