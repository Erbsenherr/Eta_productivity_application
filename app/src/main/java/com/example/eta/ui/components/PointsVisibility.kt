package com.example.eta.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the points system is on show — `UserSetup.pointsSystem`, handed down
 * from `MainScaffold`.
 *
 * A composition local rather than a field on every screen's state: the switch
 * hides figures in a dozen places that share nothing else, and threading it
 * through a dozen view models would be a dozen chances to forget one. It decides
 * only what is **drawn** — every booking goes on exactly as before, which is the
 * whole promise of the switch. The Belohn-o-mat does not read it: it stays, with
 * its numbers, as the user asked.
 */
val LocalPointsVisible = staticCompositionLocalOf { true }

/**
 * The three other "Advanced Features" — `UserSetup.growthTasks`, `contracts` and
 * `rewards` — handed down the same way and for the same reason.
 *
 * Each hides a tab and whatever else belongs to it, and stops nothing: a growth
 * task goes on growing, a contract goes on being paid as kept, a reward goes on
 * filling. All true by default, which is what a preview and a test see.
 */
data class Features(
    val growthTasks: Boolean = true,
    val contracts: Boolean = true,
    val rewards: Boolean = true,
)

val LocalFeatures = staticCompositionLocalOf { Features() }
