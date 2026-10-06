package com.example.eta.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The looks the app can wear, chosen in the settings.
 *
 * A design is a whole set of tokens — palette and corner radii — and nothing
 * else: no screen asks which one is on. Adding a third is an entry here plus a
 * branch in [colorsOf] and [shapesOf].
 */
enum class AppDesign(val label: String, val hint: String) {
    /** Built around the app icon: white, and the icon's red as the accent. */
    ETA(
        label = "Eta",
        hint = "Hell, mit dem Rot des App-Icons. Bleibt auch im Dunkelmodus hell.",
    ),

    /** [ETA] after dark: the same red, on a warm near-black instead of white. */
    ETA_DARK(
        label = "Eta Dunkel",
        hint = "Dasselbe Rot auf dunklem Grund. Bleibt auch am Tag dunkel.",
    ),

    /** The look the app had while it was called ERIK, untouched. */
    LEGACY(
        label = "Legacy",
        hint = "Das bisherige Design in Indigo. Folgt dem Dunkelmodus des Geräts.",
    );

    /**
     * The two Eta designs are each one palette, chosen by hand: light or dark
     * whatever the device says. Only the legacy one switches by itself.
     */
    val followsSystemDark: Boolean get() = this == LEGACY

    companion object {
        val DEFAULT = ETA

        /** Reads a stored name; anything unknown — or nothing — is the default. */
        fun fromStored(name: String?): AppDesign =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

fun colorsOf(design: AppDesign, systemDark: Boolean): EtaColors = when (design) {
    AppDesign.ETA -> EtaRedColors
    AppDesign.ETA_DARK -> EtaRedDarkColors
    AppDesign.LEGACY -> if (systemDark) LegacyDarkColors else LegacyLightColors
}

fun shapesOf(design: AppDesign): EtaShapes = when (design) {
    AppDesign.ETA, AppDesign.ETA_DARK -> EtaRedShapes
    AppDesign.LEGACY -> EtaShapes()
}

/**
 * Which design is on.
 *
 * In `SharedPreferences` and not in `user_setup` like the other settings: the
 * design has to be known before the first frame is drawn — a database read would
 * show one palette and then swap it — and it has to exist before the
 * questionnaire has produced a row at all. The price is that it does not travel
 * in a backup, which copies the database.
 */
class DesignStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _design = MutableStateFlow(AppDesign.fromStored(prefs.getString(KEY, null)))
    val design: StateFlow<AppDesign> = _design.asStateFlow()

    fun select(design: AppDesign) {
        prefs.edit().putString(KEY, design.name).apply()
        _design.value = design
    }

    private companion object {
        const val PREFS = "design"
        const val KEY = "design"
    }
}
