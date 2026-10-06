package com.example.eta.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.getSystemService
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Eta's voice: a task's name read out where a sound would have played.
 *
 * It follows the rules [EtaSound] set, because it stands in for it: the **alarm
 * stream**, so the notification volume is irrelevant, and **silent under "Nicht
 * stören"** and under nothing else. Music ducks for the length of a sentence.
 *
 * A process-wide object rather than something a receiver owns. The speech engine
 * is another app's service, bound asynchronously, and a receiver is gone long
 * before a sentence is over — the binding has to outlive it for as long as the
 * process does. The first sentence after a cold start pays for the binding; the
 * engine is kept afterwards.
 *
 * **What it cannot promise:** [speak] waits a bounded time, because the alarm
 * receiver calling it may only stay alive for seconds. A long note keeps being
 * read after that only while Android leaves the process alone — usually, not
 * certainly. Whether it does on a sleeping phone is something to hear on the
 * device.
 */
object EtaSpeech {

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Binding to the engine's service; a cold one takes a moment. */
    private val BIND_TIMEOUT = 3.seconds

    /**
     * How long a caller is held for the sentence to end. Short enough for an
     * alarm receiver, which gets about ten seconds in all.
     */
    private val SPEECH_WAIT = 5.seconds

    private var engine: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null

    /** Sentences still being read, each with the audio focus it holds. */
    private val speaking = ConcurrentHashMap<String, Utterance>()

    private class Utterance(val done: CompletableDeferred<Unit>, val focus: Any?)

    /**
     * Reads [text] out. False when it could not — no speech engine, or one that
     * refused — so the caller can fall back to the sound: late or plain beats
     * silent. True under Do-Not-Disturb, which is silence that was asked for.
     */
    suspend fun speak(context: Context, text: String): Boolean {
        val app = context.applicationContext
        if (text.isBlank() || EtaSound.isSilenced(app)) return true

        val available = withTimeoutOrNull(BIND_TIMEOUT) { bind(app).await() } ?: false
        val tts = engine
        if (!available || tts == null) return false

        val audio: AudioManager? = app.getSystemService()
        val id = UUID.randomUUID().toString()
        val utterance = Utterance(CompletableDeferred(), audio?.let { EtaSound.requestFocus(it) })
        speaking[id] = utterance

        val queued = runCatching { tts.speak(text, TextToSpeech.QUEUE_ADD, null, id) }.getOrNull()
        if (queued != TextToSpeech.SUCCESS) {
            finish(app, id)
            return false
        }
        withTimeoutOrNull(SPEECH_WAIT) { utterance.done.await() }
        return true
    }

    /** The engine, bound once; a failed attempt is tried again the next time. */
    private fun bind(app: Context): CompletableDeferred<Boolean> = synchronized(this) {
        ready?.let { known ->
            val failed = known.isCompleted && !known.getCompleted()
            if (!failed) return known
            runCatching { engine?.shutdown() }
        }
        val result = CompletableDeferred<Boolean>()
        ready = result
        engine = TextToSpeech(app) { status ->
            val tts = engine
            if (status != TextToSpeech.SUCCESS || tts == null) {
                result.complete(false)
                return@TextToSpeech
            }
            tts.setAudioAttributes(attributes)
            // The sentences are German whatever the phone is set to; an engine
            // without German keeps its own voice rather than staying silent.
            if (tts.isLanguageAvailable(Locale.GERMAN) >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = Locale.GERMAN
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finish(app, utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finish(app, utteranceId)
                override fun onError(utteranceId: String?, errorCode: Int) = finish(app, utteranceId)
            })
            result.complete(true)
        }
        result
    }

    private fun finish(app: Context, id: String?) {
        val utterance = speaking.remove(id ?: return) ?: return
        val audio: AudioManager? = app.getSystemService()
        if (audio != null && utterance.focus != null) EtaSound.abandonFocus(audio, utterance.focus)
        utterance.done.complete(Unit)
    }
}
