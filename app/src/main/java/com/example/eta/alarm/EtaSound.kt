package com.example.eta.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.eta.R
import com.example.eta.domain.sound.EtaSoundFile
import com.example.eta.domain.sound.SoundChoice
import com.example.eta.domain.sound.SoundEvent
import com.example.eta.domain.sound.decodeSoundChoice
import com.example.eta.domain.sound.encode
import com.example.eta.domain.sound.etaFile
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.example.eta.domain.sound.GroupAudio
import com.example.eta.domain.sound.SoundGroup
import com.example.eta.domain.sound.group
import com.example.eta.domain.sound.mayPlay
import com.example.eta.domain.sound.playbackVolume

/**
 * Every sound Eta makes, played by Eta rather than by a notification channel.
 *
 * The user's rule, and it is a different rule from Android's: **"Nicht stören"
 * silences the app, and so does a phone switched to silent or vibrate** (the
 * second since step 44) — unless the user said a group plays through it, and
 * since step 52 each of the two groups ([SoundGroup]) has both switches and a
 * volume of its own. A notification
 * channel's sound rides whatever
 * stream its usage names and is scaled by that stream's volume, so turning the
 * notification volume down to nothing used to silence the task start along with
 * every chat app — which is exactly what the user does *not* want to happen.
 *
 * So the channels are silent and this plays the file itself:
 *
 * - **On the alarm stream** (`USAGE_ALARM`). Its volume is separate from the
 *   notification and ring volumes, which is the whole point — the app sounds the
 *   same however the notification slider was left. The price is that it follows
 *   the *alarm* slider instead, and that is said in the settings.
 * - **Checked against Do-Not-Disturb first.** The alarm stream deliberately cuts
 *   through most DND configurations, so without this check the sounds would
 *   ignore the one switch the user *does* want to silence them. Any interruption
 *   filter other than "all" counts as on — priority-only, alarms-only, total
 *   silence — since each of them is the user having asked for quiet. The user
 *   can switch the check off per group ([groupAudio]); what plays then is up to
 *   the phone, which lets the alarm stream through everything but total silence.
 *
 * A useful side effect: the sound no longer depends on the notification
 * permission. A refused permission costs the banner, and no longer the sound.
 *
 * The wake alarm is the one exception and keeps its own [WakeAlarmRinger]: an
 * alarm clock that stayed quiet under DND would not be an alarm clock.
 */
/**
 * How Eta makes itself heard — every sound it has except the wake alarm.
 *
 * [SOUND] is what there was. [VIBRATE] buzzes where a sound would have played;
 * [SILENT] does neither and leaves the banner to say it. Chosen on the "Audio"
 * page of the settings and kept in `SharedPreferences`, for the reason the
 * Do-Not-Disturb switch is: the sounds are played from receivers that have a
 * context and nothing else.
 */
enum class AudioMode(val label: String) {
    SOUND("Ton"),
    VIBRATE("Vibration"),
    SILENT("Still"),
}

object EtaSound {

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Players still sounding. Held so the garbage collector cannot take one
     * mid-sound, and released when it completes.
     */
    private val playing = mutableSetOf<MediaPlayer>()

    /** Whether the user has asked the phone for quiet, in any of its forms. */
    fun isDoNotDisturbOn(context: Context): Boolean {
        val manager: NotificationManager = context.getSystemService() ?: return false
        return when (manager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL,
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            -> false

            else -> true
        }
    }

    /**
     * How [group] behaves under quiet and how loud it is (step 52) — the
     * "Alarme" and "Benachrichtigungen" pages.
     *
     * In `SharedPreferences` rather than `user_setup`, because this object is
     * called from receivers with a context and nothing else, and the answer has
     * to be there without a database read between the alarm and its sound.
     * Before step 52 there was one switch for Do-Not-Disturb, for every sound;
     * a group that has not been answered yet inherits it.
     */
    fun groupAudio(context: Context, group: SoundGroup): GroupAudio {
        val prefs = prefs(context)
        val legacy = prefs.getBoolean(KEY_IGNORE_DND, false)
        val prefix = KEY_GROUP + group.name
        return GroupAudio(
            throughDoNotDisturb = prefs.getBoolean("$prefix.dnd", legacy),
            throughSilentRinger = prefs.getBoolean("$prefix.silent", false),
            constantVolume = prefs.getInt("$prefix.volume", -1).takeIf { it >= 0 },
        )
    }

    fun setGroupAudio(context: Context, group: SoundGroup, audio: GroupAudio) {
        val prefix = KEY_GROUP + group.name
        prefs(context).edit {
            putBoolean("$prefix.dnd", audio.throughDoNotDisturb)
            putBoolean("$prefix.silent", audio.throughSilentRinger)
            putInt("$prefix.volume", audio.constantVolume ?: -1)
        }
    }

    /**
     * Whether the phone's ringer is switched to silent or to vibrate.
     *
     * The alarm stream these sounds play on has a slider of its own and ignores
     * that switch — which is why an alarm clock rings on a muted phone, and why
     * Eta's sounds did too. The user called that a bug, and for a task
     * announcement it is one: muting the phone is asking it to be quiet.
     */
    fun isRingerMuted(context: Context): Boolean {
        val audio: AudioManager = context.getSystemService() ?: return false
        return audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }

    /**
     * Whether a sound of [group] should stay unplayed right now: quiet was asked
     * for, and is respected — unless the user said this group plays through it.
     *
     * Two ways of asking, **Do-Not-Disturb** and the **ringer on silent or
     * vibrate**, each with a switch per group since step 52; see [mayPlay] for
     * how they combine. The wake alarm asks neither.
     */
    fun isSilenced(context: Context, group: SoundGroup): Boolean =
        !mayPlay(groupAudio(context, group), isDoNotDisturbOn(context), isRingerMuted(context))

    /** Where the phone's notification slider stands, 0–1. */
    private fun notificationLevel(audio: AudioManager?): Float {
        audio ?: return 1f
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
        if (max <= 0) return 1f
        return audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION).toFloat() / max
    }

    /**
     * What the alarm stream is set to while a sound of [group] plays — see
     * [playbackVolume]. Null leaves it alone; zero means the sound is skipped.
     */
    fun volumeFor(context: Context, group: SoundGroup): Float? {
        val app = context.applicationContext
        return playbackVolume(group, groupAudio(app, group), notificationLevel(app.getSystemService()))
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("sound", Context.MODE_PRIVATE)

    private const val KEY_IGNORE_DND = "ignoreDoNotDisturb"
    private const val KEY_GROUP = "group."
    private const val KEY_MODE = "audioMode"
    private const val KEY_CHOICE = "choice_"

    fun mode(context: Context): AudioMode {
        val stored = prefs(context).getString(KEY_MODE, null)
        return AudioMode.entries.firstOrNull { it.name == stored } ?: AudioMode.SOUND
    }

    fun setMode(context: Context, mode: AudioMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    /**
     * One short buzz in place of a sound.
     *
     * Not under Do-Not-Disturb unless the user said to play through it, and not
     * on a phone switched fully silent — vibrate mode is the phone asking for
     * exactly this, silent mode is it asking for nothing at all.
     */
    private fun vibrate(context: Context, group: SoundGroup) {
        val audio: AudioManager? = context.getSystemService()
        val rules = groupAudio(context, group)
        if (isDoNotDisturbOn(context) && !rules.throughDoNotDisturb) return
        if (audio?.ringerMode == AudioManager.RINGER_MODE_SILENT && !rules.throughSilentRinger) return
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService<VibratorManager>()?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService<Vibrator>()
            } ?: return
            val pattern = longArrayOf(0, 250, 150, 250)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }

    /** What [event] plays: the user's choice on the "Audio" page, or what it always had. */
    fun choiceOf(context: Context, event: SoundEvent): SoundChoice =
        decodeSoundChoice(prefs(context).getString(KEY_CHOICE + event.name, null))

    fun setChoice(context: Context, event: SoundEvent, choice: SoundChoice) {
        prefs(context).edit {
            val stored = choice.encode()
            if (stored == null) remove(KEY_CHOICE + event.name) else putString(KEY_CHOICE + event.name, stored)
        }
    }

    /**
     * The address of what [choice] plays for [event]: one of Eta's files as an
     * `android.resource` URI, a system sound as picked, and for the wake alarm's
     * default the phone's own alarm tone. Null only where a phone has no alarm
     * tone at all.
     */
    fun uriOf(context: Context, event: SoundEvent, choice: SoundChoice = choiceOf(context, event)): Uri? =
        when (choice) {
            is SoundChoice.System -> choice.uri.toUri()
            else -> choice.etaFile(event)?.let { resourceUri(context, it) } ?: phoneAlarmTone()
        }

    /** What [event] had before anything was chosen: the fallback when a choice will not play. */
    fun defaultUriOf(context: Context, event: SoundEvent): Uri? =
        uriOf(context, event, SoundChoice.Default)

    /** The phone's alarm tone, or its ringtone where it has none. */
    fun phoneAlarmTone(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun resourceUri(context: Context, file: EtaSoundFile): Uri =
        "android.resource://${context.packageName}/${rawOf(file)}".toUri()

    /**
     * Plays [event]'s sound once, unless the phone was asked for quiet and its
     * group does not play through it — see [isSilenced] — and at its group's
     * volume. Never throws.
     */
    fun play(context: Context, event: SoundEvent) {
        val app = context.applicationContext
        when (mode(app)) {
            AudioMode.SILENT -> return
            AudioMode.VIBRATE -> return vibrate(app, event.group)
            AudioMode.SOUND -> Unit
        }
        if (isSilenced(app, event.group)) return
        val volume = volumeFor(app, event.group)
        if (volume != null && volume <= 0f) return
        start(app, uriOf(app, event), fallback = defaultUriOf(app, event), volume = volume)
    }

    /** The one preview sounding, stopped by the next — tapping through a list should not pile them up. */
    private var preview: MediaPlayer? = null

    /** Whether the preview playing holds the alarm stream at a volume of its own. */
    private var previewHoldsVolume = false

    /**
     * Plays [choice] once, as a preview on the "Audio" page: whatever the mode
     * and the phone's quiet say, since the user just asked to hear it. At the
     * volume of its group, which is what the real sound will have.
     */
    fun preview(context: Context, event: SoundEvent, choice: SoundChoice) {
        previewGroup(context, event.group, uriOf(context.applicationContext, event, choice))
    }

    /** "Probe hören" on a group's page: [group]'s typical sound at [audio]'s volume, not yet saved. */
    fun previewGroup(context: Context, group: SoundGroup, audio: GroupAudio) {
        val app = context.applicationContext
        val event = if (group == SoundGroup.ALARM) SoundEvent.PLANNING else SoundEvent.TASK_START
        val volume = playbackVolume(group, audio, notificationLevel(app.getSystemService()))
        previewGroup(app, group, uriOf(app, event), volume ?: -1f)
    }

    private fun previewGroup(context: Context, group: SoundGroup, uri: Uri?, volume: Float = Float.NaN) {
        stopPreview()
        val app = context.applicationContext
        val level = when {
            !volume.isNaN() -> volume.takeIf { it >= 0f }
            else -> volumeFor(app, group)
        }
        if (level != null && level <= 0f) return
        previewHoldsVolume = level != null
        preview = start(app, uri, fallback = null, volume = level)
    }

    fun stopPreview() {
        preview?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
            val stillPlaying = synchronized(playing) { playing.remove(player) }
            // A preview stopped before its end hands the alarm stream back here.
            if (stillPlaying && previewHoldsVolume) AlarmStreamVolume.release(player.hashCode())
        }
        preview = null
        previewHoldsVolume = false
    }

    /**
     * Starts [uri] once, or [fallback] if it will not play — a system sound can
     * vanish with an update, or lie on storage Eta may not read. Never throws.
     */
    private fun start(app: Context, uri: Uri?, fallback: Uri?, volume: Float? = null): MediaPlayer? {
        val audio: AudioManager? = app.getSystemService()
        val session = audio?.generateAudioSessionId() ?: AudioManager.AUDIO_SESSION_ID_GENERATE
        fun create(target: Uri?): MediaPlayer? =
            target?.let { runCatching { MediaPlayer.create(app, it, null, attributes, session) }.getOrNull() }

        return runCatching {
            val player = create(uri) ?: create(fallback.takeIf { it != uri }) ?: return null
            val focus = audio?.let { requestFocus(it) }
            // The alarm stream at the group's volume for exactly as long as this plays.
            val holder = player.hashCode()
            if (volume != null) AlarmStreamVolume.acquire(app, holder, volume)

            player.setOnCompletionListener { done ->
                val wasPlaying = synchronized(playing) { playing.remove(done) }
                runCatching { done.release() }
                if (audio != null && focus != null) abandonFocus(audio, focus)
                if (wasPlaying && volume != null) AlarmStreamVolume.release(holder)
            }
            synchronized(playing) { playing += player }
            player.start()
            player
        }.getOrNull()
    }

    /**
     * Asks music to duck for the length of the sound rather than talking over it.
     * Declined focus changes nothing: the sound plays either way. Shared with
     * [EtaSpeech], which stands in for a sound and should be made room for
     * the same way.
     */
    internal fun requestFocus(audio: AudioManager): Any? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
                .also { audio.requestAudioFocus(it) }
        } else {
            null
        }

    internal fun abandonFocus(audio: AudioManager, focus: Any) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focus is AudioFocusRequest) {
            runCatching { audio.abandonAudioFocusRequest(focus) }
        }
    }
}

/**
 * Posts a notification if the user allows it, and does nothing if not.
 *
 * Every alarm here posts from a receiver and has already done the part that
 * matters by then — rescheduled, played its sound — so a refused permission may
 * cost the banner and nothing else. The explicit check is also what the
 * `MissingPermission` lint asks for; a `runCatching` around `notify` swallowed
 * the same failure without saying so.
 */
internal fun postNotification(context: Context, tag: String?, id: Int, notification: Notification) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    try {
        clearEarlierNotifications(context)
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    } catch (revoked: SecurityException) {
        // Revoked between the check and the call; the same outcome as refused.
    }
}

/**
 * How long ago a notification may have been posted and still stand beside a new
 * one: two tasks starting on the same minute are posted one after the other, and
 * the second must not take the first away.
 */
private const val SAME_MOMENT_MILLIS = 10_000L

/**
 * Takes Eta's earlier notifications off the lock screen before a new one is
 * posted (step 53), so only the latest stands there instead of a day's worth.
 *
 * Spared: an **ongoing** one — the wake alarm, which is answered rather than
 * read — and what was posted in the same moment. Only Eta's own: the system
 * hands an app nothing but its own notifications.
 */
private fun clearEarlierNotifications(context: Context) {
    val manager: NotificationManager = context.getSystemService() ?: return
    val now = System.currentTimeMillis()
    runCatching { manager.activeNotifications }.getOrNull().orEmpty()
        .filter { it.notification.flags and Notification.FLAG_ONGOING_EVENT == 0 }
        .filter { now - it.postTime > SAME_MOMENT_MILLIS }
        .forEach { manager.cancel(it.tag, it.id) }
}

/**
 * A notification channel that makes no sound of its own — [EtaSound] plays it.
 *
 * A channel's sound is fixed when the channel is created, so every channel that
 * used to carry one needs a **new id** to become silent, and the old id is
 * deleted with it so no dead entry lingers in the system settings. That is what
 * [legacyIds] is for.
 */
internal fun ensureSilentChannel(
    context: Context,
    id: String,
    name: String,
    description: String,
    legacyIds: List<String> = emptyList(),
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager: NotificationManager = context.getSystemService() ?: return
    legacyIds.forEach { manager.deleteNotificationChannel(it) }
    manager.createNotificationChannel(
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = description
            setSound(null, null)
        },
    )
}

/** The resource of one of Eta's files. */
internal fun rawOf(file: EtaSoundFile): Int = when (file) {
    EtaSoundFile.TASK_START -> R.raw.task_start
    EtaSoundFile.TASK_END -> R.raw.task_end
    EtaSoundFile.STILL_ACTIVE -> R.raw.still_active
    EtaSoundFile.POM_WORK_START -> R.raw.pom_work_start
    EtaSoundFile.POM_PAUSE_START -> R.raw.pom_pause_start
    EtaSoundFile.PLANNING_START -> R.raw.planning_start
    EtaSoundFile.NOTIFICATION -> R.raw.notification
}
