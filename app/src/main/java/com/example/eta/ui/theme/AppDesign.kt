package com.example.eta.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The looks the app can wear, chosen in the settings.
 *
 * A design is a whole set of tokens — a palette by day, one by night, and corner
 * radii — and nothing else: no screen asks which one is on. Adding a third is an
 * entry here plus a branch in [colorsOf] and [shapesOf]. Whether the day or the
 * night palette is worn is a separate answer, [Brightness].
 */
enum class AppDesign(val label: String, val hint: String) {
    /** Built around the app icon: white or near-black, and the icon's red as the accent. */
    ETA(
        label = "Eta",
        hint = "Weiß oder fast schwarz, mit dem Rot des App-Icons.",
    ),

    /** The look the app had while it was called ERIK, untouched. */
    LEGACY(
        label = "Legacy",
        hint = "Das bisherige Design, mit Indigo statt Rot.",
    );

    companion object {
        val DEFAULT = ETA
    }
}

/** Whether a design wears its day palette, its night palette, or asks the phone. */
enum class Brightness(val label: String) {
    SYSTEM("Dem Handy anpassen"),
    LIGHT("Hell"),
    DARK("Dunkel");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    /** The one after this, round and round: one button steps through all three. */
    fun next(): Brightness = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = SYSTEM
    }
}

/** What the settings' Design card holds: which look, and how bright. */
data class DesignChoice(
    val design: AppDesign = AppDesign.DEFAULT,
    val brightness: Brightness = Brightness.DEFAULT,
) {
    fun colors(systemDark: Boolean): EtaColors = colorsOf(design, brightness.isDark(systemDark))

    companion object {
        /**
         * Reads the two stored names; anything unknown — or nothing — is the default.
         *
         * `ETA_DARK` was a design of its own for one build, before the brightness
         * became a separate answer. Someone who had chosen it gets what they chose.
         */
        fun fromStored(design: String?, brightness: String?): DesignChoice {
            val storedBrightness = Brightness.entries.firstOrNull { it.name == brightness }
            if (design == "ETA_DARK") {
                return DesignChoice(AppDesign.ETA, storedBrightness ?: Brightness.DARK)
            }
            return DesignChoice(
                design = AppDesign.entries.firstOrNull { it.name == design } ?: AppDesign.DEFAULT,
                brightness = storedBrightness ?: Brightness.DEFAULT,
            )
        }
    }
}

fun colorsOf(design: AppDesign, dark: Boolean): EtaColors = when (design) {
    AppDesign.ETA -> if (dark) EtaRedDarkColors else EtaRedColors
    AppDesign.LEGACY -> if (dark) LegacyDarkColors else LegacyLightColors
}

fun shapesOf(design: AppDesign): EtaShapes = when (design) {
    AppDesign.ETA -> EtaRedShapes
    AppDesign.LEGACY -> EtaShapes()
}

/**
 * Which design is on, and how bright.
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

    private val _choice = MutableStateFlow(
        DesignChoice.fromStored(
            design = prefs.getString(KEY_DESIGN, null),
            brightness = prefs.getString(KEY_BRIGHTNESS, null),
        ),
    )
    val choice: StateFlow<DesignChoice> = _choice.asStateFlow()

    fun select(design: AppDesign) = store(_choice.value.copy(design = design))

    fun select(brightness: Brightness) = store(_choice.value.copy(brightness = brightness))

    private fun store(choice: DesignChoice) {
        prefs.edit()
            .putString(KEY_DESIGN, choice.design.name)
            .putString(KEY_BRIGHTNESS, choice.brightness.name)
            .apply()
        _choice.value = choice
    }

    private companion object {
        const val PREFS = "design"
        const val KEY_DESIGN = "design"
        const val KEY_BRIGHTNESS = "brightness"
    }
}
