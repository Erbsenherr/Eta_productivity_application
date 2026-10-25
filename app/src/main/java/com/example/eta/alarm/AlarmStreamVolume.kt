package com.example.eta.alarm

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.example.eta.domain.sound.streamStep

/**
 * The alarm stream held at a volume of Eta's choosing while a sound plays, and
 * handed back afterwards (step 52).
 *
 * Every sound of Eta's rides the alarm stream, so a sound can only be louder or
 * quieter than the alarm slider by moving that slider for its length. That is
 * what a "konstante Lautstärke" — and a notification bound to the notification
 * slider — needs. Several sounds may overlap: the level the user had is kept
 * from the first one on and put back when the last one ends.
 *
 * **What is borrowed is written down** (`SharedPreferences`), so a process
 * killed in the middle of a sound does not leave the user's alarm slider moved:
 * [restoreLeftover] puts it back when the app next starts. The price, known:
 * the phone's own alarm clock ringing during one of Eta's sounds rings at
 * Eta's volume for those seconds.
 */
object AlarmStreamVolume {

    private val holders = mutableSetOf<Int>()

    // The application context only, which lives as long as the process anyway.
    @SuppressLint("StaticFieldLeak")
    private var app: Context? = null

    /** Sets the alarm stream to [fraction] of its range for [holder], until [release]. */
    fun acquire(context: Context, holder: Int, fraction: Float) {
        val appContext = context.applicationContext
        val audio: AudioManager = appContext.getSystemService() ?: return
        synchronized(this) {
            if (holders.isEmpty()) {
                val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                prefs(appContext).edit { putInt(KEY_SAVED, current) }
            }
            holders += holder
            app = appContext
        }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, streamStep(fraction, max), 0) }
    }

    /** [holder] is done; the last one out puts the user's level back. */
    fun release(holder: Int) {
        val context = synchronized(this) {
            if (!holders.remove(holder) || holders.isNotEmpty()) return
            app
        } ?: return
        restore(context)
    }

    /** At app start: a level still written down was never put back — a process died mid-sound. */
    fun restoreLeftover(context: Context) {
        synchronized(this) { if (holders.isNotEmpty()) return }
        restore(context.applicationContext)
    }

    private fun restore(context: Context) {
        val prefs = prefs(context)
        val saved = prefs.getInt(KEY_SAVED, -1)
        if (saved < 0) return
        val audio: AudioManager? = context.getSystemService()
        runCatching { audio?.setStreamVolume(AudioManager.STREAM_ALARM, saved, 0) }
        prefs.edit { remove(KEY_SAVED) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("sound", Context.MODE_PRIVATE)

    private const val KEY_SAVED = "borrowedAlarmVolume"
}
