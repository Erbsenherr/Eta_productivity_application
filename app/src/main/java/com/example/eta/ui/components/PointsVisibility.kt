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
