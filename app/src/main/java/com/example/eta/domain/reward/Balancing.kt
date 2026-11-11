package com.example.eta.domain.reward

/**
 * Every point source and multiplier the debug page's "Punkte-Balancing" can
 * tune (step 58), with the value it had as a constant — which is what an
 * untouched install still runs on.
 *
 * Two more are tuned there that were settings already and stay on
 * `UserSetup`: what an hour called off costs, and what an unplanned hour
 * costs.
 */
enum class BalanceKey(
    val label: String,
    val hint: String,
    val default: Double,
    /** What one tap of the stepper moves it by. */
    val step: Double,
    /** A share rather than points: shown in percent. */
    val share: Boolean = false,
) {
    FOCUS("Fokuszeit", "Punkte je Stunde", 1.0, 0.1),
    SIDELINE("Nebenbei", "Punkte je Stunde", 0.5, 0.1),
    MINDFUL("Achtsamkeit", "Punkte je Stunde", 1.0, 0.1),
    CALENDAR("Kalendertermin", "Punkte je volle Stunde", 0.5, 0.1),
    PUNCTUAL_WAKE("Pünktlich aufstehen", "Punkte am Abend", 0.5, 0.1),
    PUNCTUAL_BED("Pünktlich schlafen gehen", "Punkte, mit dem Tag danach", 0.5, 0.1),
    FREE_TIME_FORGONE("Freizeit aufgeben", "Punkte je Stunde verzichteter Freizeit", 4.0, 0.5),
    FREE_UNPLANNED_HOURS("Unverplante Zeit frei", "Stunden am Tag ohne Abzug", 2.0, 0.5),
    LEGACY_CAP("Altverträge", "höchstens so viele Punkte am Tag, zusammen", 2.0, 0.5),
    CONTRACT_BREACH("Vertragsbruch", "Punkte Abzug", 10.0, 1.0),
    STREAK_BREAK("Streak reißt", "Punkte Abzug", 10.0, 1.0),
    STREAK_PER_DAY("Streak", "Punkte je Tag Länge, jeden Abend", 0.001, 0.001),
    STREAK_MAX_PER("Streak: Höchstwert", "Punkte je Streak und Abend", 1.0, 0.1),
    STREAK_MAX_TOTAL("Streaks: Höchstwert", "Punkte aller Streaks am Abend", 3.0, 0.5),
    WEEKLY_INFLATION("Wertverfall", "Anteil des Kontos pro Woche", 0.30, 0.05, share = true),
    EARLY_END_MALUS("Vorzeitig beenden", "Abzug von der Zeit einer Timer-Task", 0.10, 0.01, share = true),
    MALUS_RECOVERY("Erholung", "zurück je voller Sitzung, von der alten Zeit", 0.02, 0.01, share = true),
}

/**
 * The values in force. Set from the stored setup whenever it changes
 * (`EtaApplication`), read by the arithmetic where it used to read constants —
 * one global, because the arithmetic is pure and reached from a dozen places
 * that have no setup to hand. Unset, every key is its [BalanceKey.default], so
 * the tests and a fresh install see the rules as they always were.
 */
object Balancing {
    @Volatile
    var values: Map<BalanceKey, Double> = emptyMap()

    operator fun get(key: BalanceKey): Double = values[key] ?: key.default
}

/** "FOCUS=1.2,STREAK_BREAK=5.0" — only what differs from its default is written. */
fun encodeBalancing(values: Map<BalanceKey, Double>): String =
    values.filter { (key, value) -> value != key.default }
        .entries
        .sortedBy { it.key.ordinal }
        .joinToString(",") { "${it.key.name}=${it.value}" }

/** The other way; anything unreadable is left at its default. */
fun decodeBalancing(text: String): Map<BalanceKey, Double> =
    text.split(',')
        .mapNotNull { part ->
            val (name, value) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val key = BalanceKey.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { key to it }
        }
        .toMap()
