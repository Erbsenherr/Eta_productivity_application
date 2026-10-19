package com.example.eta.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
    /**
     * An accent of the user's own, as ARGB, in place of the design's — null is
     * the design's. A third answer beside the other two: it goes over whichever
     * design and whichever brightness are on.
     */
    val accent: Int? = null,
    /** What the tab bar shows — a look, and so kept with the looks. */
    val tabStyle: TabStyle = TabStyle.DEFAULT,
    /**
     * The tabs in the user's own order, by name (step 48); empty is the app's.
     * Hidden tabs keep their place in it, so switching a feature on brings its
     * tab back where it was — see [orderTabs].
     */
    val tabOrder: List<String> = emptyList(),
) {
    fun colors(systemDark: Boolean): EtaColors {
        val base = colorsOf(design, brightness.isDark(systemDark))
        return accent?.let { base.withAccent(Color(it)) } ?: base
    }

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

        /** The same, with the two answers of step 44 beside them. */
        fun fromStored(design: String?, brightness: String?, accent: Int?, tabStyle: String?): DesignChoice =
            fromStored(design, brightness).copy(
                accent = accent,
                tabStyle = TabStyle.entries.firstOrNull { it.name == tabStyle } ?: TabStyle.DEFAULT,
            )
    }
}

/**
 * What the tab bar draws for each destination: its name, an icon, or both.
 *
 * Names are the default and were the only answer until step 44 — the
 * destinations are words this app can say in full. Icons alone make the bar
 * short enough that most of it fits without scrolling.
 */
enum class TabStyle(val label: String) {
    NAMES("Nur Namen"),
    ICONS("Nur Icons"),
    BOTH("Icons und Namen");

    val showsIcon: Boolean get() = this != NAMES
    val showsName: Boolean get() = this != ICONS

    companion object {
        val DEFAULT = NAMES
    }
}

/**
 * The palette with an accent of the user's choosing.
 *
 * Three tokens move together, or the accent would sit on tokens tuned for
 * another colour: the accent itself, the wash behind accented things — the
 * accent thinned onto the palette's own surface, so it is right by day and by
 * night — and what is written **on** the accent, black or white by whichever
 * the colour is far enough from. Nothing else changes: the category colours,
 * the warnings and the launch screen are not the accent's.
 */
fun EtaColors.withAccent(accent: Color): EtaColors = copy(
    accent = accent,
    accentSoft = accent.copy(alpha = 0.16f).compositeOver(surface),
    onAccent = if (accent.luminance() > 0.45f) Color(0xFF14142B) else Color.White,
)

/**
 * The accents offered as swatches, beside the free choice of a hue.
 *
 * Mid-tones on purpose: each has to carry white text as a button and still be
 * readable as text on white and on near-black.
 */
val ACCENT_PRESETS: List<Int> = listOf(
    0xFFE5322D, 0xFFE0662B, 0xFFC99A1E, 0xFF4E9F3D, 0xFF1F9E8F, 0xFF2A8FD0,
    0xFF4F5BD5, 0xFF8A4FD5, 0xFFC94FB0, 0xFFD5456E, 0xFF6B7280,
).map { it.toInt() }

/** A hue of the user's own, at the saturation and value the presets sit at. */
fun accentOfHue(hue: Float): Int =
    Color.hsv(hue.coerceIn(0f, 359.9f), 0.72f, 0.82f).toArgb()

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
            accent = if (prefs.contains(KEY_ACCENT)) prefs.getInt(KEY_ACCENT, 0) else null,
            tabStyle = prefs.getString(KEY_TAB_STYLE, null),
        ).copy(
            tabOrder = prefs.getString(KEY_TAB_ORDER, null)
                ?.split(',')
                ?.filter { it.isNotBlank() }
                .orEmpty(),
        ),
    )
    val choice: StateFlow<DesignChoice> = _choice.asStateFlow()

    fun select(design: AppDesign) = store(_choice.value.copy(design = design))

    fun select(brightness: Brightness) = store(_choice.value.copy(brightness = brightness))

    /** An accent of the user's own, or null for the design's. */
    fun selectAccent(accent: Int?) = store(_choice.value.copy(accent = accent))

    fun select(tabStyle: TabStyle) = store(_choice.value.copy(tabStyle = tabStyle))

    /** The tabs in an order of the user's own, every one of them by name. */
    fun selectTabOrder(order: List<String>) = store(_choice.value.copy(tabOrder = order))

    private fun store(choice: DesignChoice) {
        prefs.edit()
            .putString(KEY_DESIGN, choice.design.name)
            .putString(KEY_BRIGHTNESS, choice.brightness.name)
            .putString(KEY_TAB_STYLE, choice.tabStyle.name)
            .putString(KEY_TAB_ORDER, choice.tabOrder.joinToString(","))
            .apply { if (choice.accent == null) remove(KEY_ACCENT) else putInt(KEY_ACCENT, choice.accent) }
            .apply()
        _choice.value = choice
    }

    private companion object {
        const val PREFS = "design"
        const val KEY_DESIGN = "design"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_ACCENT = "accent"
        const val KEY_TAB_STYLE = "tabStyle"
        const val KEY_TAB_ORDER = "tabOrder"
    }
}

/**
 * The tabs in the order the user gave them (step 48).
 *
 * [defaults] is the app's own order, [stored] the user's. A name the stored
 * order does not know — a tab added by a later update — goes in right after the
 * tab it follows in the app's order, so it turns up where it would have stood
 * rather than behind the settings. A stored name that no longer exists is
 * dropped.
 */
fun orderTabs(defaults: List<String>, stored: List<String>): List<String> {
    val result = stored.filter { it in defaults }.distinct().toMutableList()
    defaults.forEachIndexed { index, name ->
        if (name in result) return@forEachIndexed
        val before = defaults.subList(0, index).lastOrNull { it in result }
        result.add(if (before == null) 0 else result.indexOf(before) + 1, name)
    }
    return result
}

/**
 * [full] with the tabs of [visible] put into the order given there, each hidden
 * tab keeping the slot it had — the menu only lists what the bar shows.
 */
fun reorderVisible(full: List<String>, visible: List<String>): List<String> {
    val queue = ArrayDeque(visible)
    return full.map { name -> if (name in visible) queue.removeFirst() else name }
}
