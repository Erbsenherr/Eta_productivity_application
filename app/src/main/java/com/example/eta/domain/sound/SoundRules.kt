package com.example.eta.domain.sound

/**
 * The two kinds of sound Eta makes, each with rules of its own (step 52).
 *
 * [ALARM] is what rings at an hour that was set: the wake alarm and the
 * planning phases. [NOTIFICATION] is everything else — a task beginning or
 * ending, the pomodoro turns, the timer, reminders, the Belohn-o-mat and the
 * streaks, and the task names read aloud.
 */
enum class SoundGroup(val label: String) {
    ALARM("Alarme"),
    NOTIFICATION("Benachrichtigungen"),
}

/** Which group a moment belongs to. */
val SoundEvent.group: SoundGroup
    get() = when (this) {
        SoundEvent.WAKE, SoundEvent.PLANNING -> SoundGroup.ALARM
        else -> SoundGroup.NOTIFICATION
    }

/**
 * How one [SoundGroup] behaves when the phone was asked for quiet, and how loud
 * it is.
 *
 * Both quiet switches are the user's, in every combination: through
 * Do-Not-Disturb or not, through a phone on silent or vibrate or not.
 * [constantVolume] is a percentage of the alarm stream's range, or null for
 * "wie das Handy" — the alarm slider for alarms, the notification slider for
 * notifications.
 */
data class GroupAudio(
    val throughDoNotDisturb: Boolean = false,
    val throughSilentRinger: Boolean = false,
    val constantVolume: Int? = null,
)

/** The level a new constant volume starts at. */
const val DEFAULT_CONSTANT_VOLUME = 70

/**
 * Whether a sound of a group with [audio] may play right now.
 *
 * **While Do-Not-Disturb is on, its switch alone decides**: most phones report
 * the ringer as silent under DND, and reading both would make the DND switch do
 * nothing for anyone who left the other one off. With DND off, a ringer on
 * silent or vibrate silences the group unless it plays through that too.
 */
fun mayPlay(audio: GroupAudio, doNotDisturb: Boolean, ringerMuted: Boolean): Boolean =
    if (doNotDisturb) audio.throughDoNotDisturb else !ringerMuted || audio.throughSilentRinger

/**
 * What the alarm stream — which every sound of Eta's rides — is set to for the
 * length of a sound, as a fraction of its range; null leaves it as it is.
 *
 * A constant volume is that volume, whatever the phone's sliders say: the point
 * of it is to be heard with the notification volume at nothing. Bound to the
 * phone, an alarm keeps the alarm slider (null), and a notification takes the
 * notification slider's position ([notificationLevel], 0–1) — so a notification
 * volume of zero means silence, as "wie das Handy" promises. Zero is never
 * played: the caller skips the sound.
 */
fun playbackVolume(group: SoundGroup, audio: GroupAudio, notificationLevel: Float): Float? =
    when {
        audio.constantVolume != null -> audio.constantVolume.coerceIn(0, 100) / 100f
        group == SoundGroup.NOTIFICATION -> notificationLevel.coerceIn(0f, 1f)
        else -> null
    }

/** The alarm stream's step for [fraction] of a range of [max] steps; at least 1 for anything audible. */
fun streamStep(fraction: Float, max: Int): Int =
    if (fraction <= 0f || max <= 0) 0 else (fraction * max).toInt().coerceIn(1, max)
