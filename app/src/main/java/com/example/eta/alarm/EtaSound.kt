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
import androidx.annotation.RawRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Every sound Eta makes, played by Eta rather than by a notification channel.
 *
 * The user's rule, and it is a different rule from Android's: **"Nicht stören"
 * silences the app, and so does a phone switched to silent or vibrate** (the
 * second since step 44) — the notification *volume* does not. A notification
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
 *   can switch the check off ([ignoresDoNotDisturb]); what plays then is up to
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
     * Whether the user chose to be told through Do-Not-Disturb as well.
     *
     * Off unless switched on in the Alarme card. In `SharedPreferences` rather
     * than `user_setup`, because this object is called from receivers with a
     * context and nothing else, and the answer has to be there without a
     * database read between the alarm and its sound.
     */
    fun ignoresDoNotDisturb(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IGNORE_DND, false)

    fun setIgnoresDoNotDisturb(context: Context, ignore: Boolean) {
        prefs(context).edit().putBoolean(KEY_IGNORE_DND, ignore).apply()
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
     * Whether a sound should stay unplayed right now: quiet was asked for, and
     * is respected.
     *
     * Two ways of asking. **Do-Not-Disturb**, which the user may tell Eta to
     * play through ([ignoresDoNotDisturb]) — and while it is on, that switch
     * alone decides, since most phones report the ringer as silent under it and
     * the switch would otherwise do nothing. And the **ringer on silent or
     * vibrate**, which has no way round it. The wake alarm asks neither.
     */
    fun isSilenced(context: Context): Boolean =
        if (isDoNotDisturbOn(context)) !ignoresDoNotDisturb(context) else isRingerMuted(context)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("sound", Context.MODE_PRIVATE)

    private const val KEY_IGNORE_DND = "ignoreDoNotDisturb"
    private const val KEY_MODE = "audioMode"

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
    private fun vibrate(context: Context) {
        val audio: AudioManager? = context.getSystemService()
        if (isDoNotDisturbOn(context) && !ignoresDoNotDisturb(context)) return
        if (audio?.ringerMode == AudioManager.RINGER_MODE_SILENT) return
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

    /** Plays [sound] once, unless the phone was asked for quiet — see [isSilenced]. Never throws. */
    fun play(context: Context, @RawRes sound: Int) {
        val app = context.applicationContext
        when (mode(app)) {
            AudioMode.SILENT -> return
            AudioMode.VIBRATE -> return vibrate(app)
            AudioMode.SOUND -> Unit
        }
        if (isSilenced(app)) return
        val audio: AudioManager? = app.getSystemService()

        runCatching {
            val player = MediaPlayer.create(
                app,
                sound,
                attributes,
                audio?.generateAudioSessionId() ?: AudioManager.AUDIO_SESSION_ID_GENERATE,
            ) ?: return
            val focus = audio?.let { requestFocus(it) }

            player.setOnCompletionListener { done ->
                synchronized(playing) { playing.remove(done) }
                runCatching { done.release() }
                if (audio != null && focus != null) abandonFocus(audio, focus)
            }
            synchronized(playing) { playing += player }
            player.start()
        }
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
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    } catch (revoked: SecurityException) {
        // Revoked between the check and the call; the same outcome as refused.
    }
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
