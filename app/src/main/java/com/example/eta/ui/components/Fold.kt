package com.example.eta.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether one section of a screen is open, remembered for good.
 *
 * Not `rememberSaveable`: that survives a rotation and nothing else, and a list
 * the user folded away on Monday should still be folded away on Tuesday. So the
 * answer is written down the moment it changes — in `SharedPreferences`, since
 * it is how a screen is arranged on this phone and not part of the user's data.
 */
@Stable
class Fold internal constructor(
    private val prefs: SharedPreferences,
    private val key: String,
    initiallyOpen: Boolean,
) {
    var open: Boolean by mutableStateOf(prefs.getBoolean(key, initiallyOpen))
        private set

    fun toggle() {
        open = !open
        prefs.edit().putBoolean(key, open).apply()
    }
}

/** [key] names the section across the whole app, so prefix it with its screen. */
@Composable
fun rememberFold(key: String, initiallyOpen: Boolean = false): Fold {
    val context = LocalContext.current.applicationContext
    return remember(key) {
        Fold(context.getSharedPreferences("folds", Context.MODE_PRIVATE), key, initiallyOpen)
    }
}
