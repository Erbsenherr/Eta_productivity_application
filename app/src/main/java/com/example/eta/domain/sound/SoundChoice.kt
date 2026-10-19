package com.example.eta.domain.sound

/**
 * The sound files Eta ships, by the name of their file in `res/raw`.
 *
 * Named here rather than by resource id so this stays plain Kotlin and a stored
 * choice survives a rebuild — `R.raw` ids are not stable across builds.
 * `alarm/EtaSounds.kt` maps each to its resource.
 */
enum class EtaSoundFile(val label: String) {
    TASK_START("Eta: Aufgabe beginnt"),
    TASK_END("Eta: Aufgabe endet"),
    STILL_ACTIVE("Eta: Noch dabei?"),
    POM_WORK_START("Eta: Pomodoro-Arbeit"),
    POM_PAUSE_START("Eta: Pomodoro-Pause"),
    PLANNING_START("Eta: Planungsphase"),
    NOTIFICATION("Eta: Hinweis"),
}

/**
 * Every moment Eta makes a sound, each with its own choice.
 *
 * [default] is the file it had before sounds could be chosen; null for the wake
 * alarm, whose default is the phone's own alarm tone.
 */
enum class SoundEvent(val label: String, val default: EtaSoundFile?) {
    TASK_START("Aufgabe beginnt", EtaSoundFile.TASK_START),
    TASK_END("Geplantes Ende einer Aufgabe", EtaSoundFile.TASK_END),
    STILL_ACTIVE("Bin ich noch bei der Sache?", EtaSoundFile.STILL_ACTIVE),
    POMODORO_WORK("Pomodoro: Arbeit beginnt", EtaSoundFile.POM_WORK_START),
    POMODORO_PAUSE("Pomodoro: Pause beginnt", EtaSoundFile.POM_PAUSE_START),
    // The user's choice at step 42: the timer running out sounds like a pause beginning.
    TIMER_DONE("Timer-Zeit erreicht", EtaSoundFile.POM_PAUSE_START),
    PLANNING("Planungsphase beginnt", EtaSoundFile.PLANNING_START),
    REMINDER("Erinnerung", EtaSoundFile.NOTIFICATION),
    REWARD_FILL("Belohn-o-mat füllt sich", EtaSoundFile.NOTIFICATION),
    REWARD_FULL("Belohnung erreicht", EtaSoundFile.POM_WORK_START),
    STREAK_UP("Streak wächst", EtaSoundFile.NOTIFICATION),
    STREAK_OUT("Streak erlischt", EtaSoundFile.POM_PAUSE_START),
    WAKE("Weckruf", null),
}

/** What one [SoundEvent] plays. */
sealed interface SoundChoice {
    /** What it had before anything was chosen — [SoundEvent.default]. */
    data object Default : SoundChoice

    data class Eta(val file: EtaSoundFile) : SoundChoice

    /** A sound of the phone's, picked in Android's own list: its content URI. */
    data class System(val uri: String) : SoundChoice
}

private const val ETA_PREFIX = "eta:"

/** How a choice is kept in `SharedPreferences`; null for [SoundChoice.Default], which is kept as nothing. */
fun SoundChoice.encode(): String? = when (this) {
    SoundChoice.Default -> null
    is SoundChoice.Eta -> ETA_PREFIX + file.name
    is SoundChoice.System -> uri
}

/**
 * The stored value read back. Anything unreadable — a file of Eta's that no
 * longer exists, an empty string — is [SoundChoice.Default] rather than silence.
 */
fun decodeSoundChoice(stored: String?): SoundChoice {
    if (stored.isNullOrBlank()) return SoundChoice.Default
    if (stored.startsWith(ETA_PREFIX)) {
        val name = stored.removePrefix(ETA_PREFIX)
        return EtaSoundFile.entries.firstOrNull { it.name == name }
            ?.let { SoundChoice.Eta(it) }
            ?: SoundChoice.Default
    }
    return SoundChoice.System(stored)
}

/**
 * The Eta file a choice comes down to, or null where it is a system sound —
 * or the wake alarm's default, the phone's alarm tone.
 */
fun SoundChoice.etaFile(event: SoundEvent): EtaSoundFile? = when (this) {
    SoundChoice.Default -> event.default
    is SoundChoice.Eta -> file
    is SoundChoice.System -> null
}
