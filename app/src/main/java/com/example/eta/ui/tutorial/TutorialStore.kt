package com.example.eta.ui.tutorial

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TutorialState(
    /** The welcome page before the questionnaire has been read. */
    val introSeen: Boolean,
    /** The first run still owes its tutorial, once the questionnaire is done. */
    val owed: Boolean,
    /** Asked for from the settings. Not kept across a restart. */
    val requested: Boolean = false,
) {
    val active: Boolean get() = owed || requested
}

/**
 * Whether the tutorial is to be shown.
 *
 * In `SharedPreferences`, like the design and the folds, and for the reason the
 * design gives: it has to be known before there is a setup row — the welcome
 * page comes *before* the questionnaire — and it is a fact about this install,
 * not part of the user's data. So it does not travel in a backup.
 *
 * **Only a first run owes one.** Someone updating an app they already use has a
 * setup and no flag here; they are not marched through a tutorial they did not
 * ask for, and find it in the settings instead.
 */
class TutorialStore(context: Context) {

    private val prefs = context.getSharedPreferences("tutorial", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        TutorialState(
            introSeen = prefs.getBoolean(KEY_INTRO_SEEN, false),
            owed = prefs.getBoolean(KEY_OWED, false),
        ),
    )
    val state: StateFlow<TutorialState> = _state.asStateFlow()

    /** The welcome page was read: on to the questionnaire, the tutorial after it. */
    fun introDone() = write(introSeen = true, owed = true)

    /** "Tutorial wiederholen" in the settings. */
    fun request() = _state.update { it.copy(requested = true) }

    /** Finished, skipped or walked out of — it is not shown again by itself. */
    fun finish() {
        write(introSeen = _state.value.introSeen, owed = false)
        _state.update { it.copy(requested = false) }
    }

    /** The debug reset: back to before the first start, welcome page included. */
    fun reset() {
        write(introSeen = false, owed = false)
        _state.update { it.copy(requested = false) }
    }

    private fun write(introSeen: Boolean, owed: Boolean) {
        prefs.edit().putBoolean(KEY_INTRO_SEEN, introSeen).putBoolean(KEY_OWED, owed).apply()
        _state.update { it.copy(introSeen = introSeen, owed = owed) }
    }

    private companion object {
        const val KEY_INTRO_SEEN = "introSeen"
        const val KEY_OWED = "owed"
    }
}
