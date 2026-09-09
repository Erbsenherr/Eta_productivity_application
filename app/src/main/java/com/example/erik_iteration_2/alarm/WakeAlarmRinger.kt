package com.example.erik_iteration_2.alarm

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService

/**
 * The sound and the buzz, held outside any screen.
 *
 * Process-wide rather than owned by [WakeAlarmActivity], because the two can come
 * apart: the full-screen intent may be refused, the activity may be rotated away
 * and rebuilt, and the "Aus" button lives on the notification, which is a
 * receiver. A ringer tied to a screen would keep playing after the screen was
 * gone, which is the one failure a wake alarm must not have.
 *
 * [start] is idempotent — a second call while it is already ringing does nothing
 * rather than layering a second player on top.
 */
object WakeAlarmRinger {

    private var player: MediaPlayer? = null

    val isRinging: Boolean get() = player != null

    fun start(context: Context) {
        if (player != null) return
        val uri = WakeAlarmNotifications.soundUri(context) ?: return

        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(WakeAlarmNotifications.audioAttributes)
                setDataSource(context.applicationContext, uri)
                // Looping is what separates an alarm from a notification: a tone
                // that plays once and stops cannot wake anyone who slept through
                // its twenty seconds.
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()

        vibrate(context)
    }

    fun stop(context: Context) {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        runCatching { vibrator(context)?.cancel() }
    }

    private fun vibrate(context: Context) {
        val vibrator = vibrator(context) ?: return
        val pattern = longArrayOf(0, 700, 900)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService()
        }
}
