package com.meditation.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.meditation.core.SessionStage
import java.util.Locale

/**
 * Spoken-cue narration (brief §15). Announces a stage's name and any of its free-text
 * [SessionStage.spokenCueIds] when the session enters that stage, and a closing phrase on
 * completion. Queued (not flushed) so a stage name and its cues are heard in order without
 * cutting each other off. Initialization is asynchronous; anything spoken before the engine is
 * ready is dropped rather than crashing or blocking the session.
 */
class TtsController(context: Context) {

    private var ready = false
    private val pending = mutableListOf<String>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            pending.forEach { enqueue(it) }
            pending.clear()
        }
    }

    init {
        runCatching { tts.language = Locale.getDefault() }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = Unit
            @Deprecated("required override") override fun onError(utteranceId: String?) = Unit
        })
    }

    /** Speak the stage's name, then each spoken cue in order. No-op if [enabled] is false. */
    fun announceStage(stage: SessionStage, enabled: Boolean) {
        if (!enabled) return
        speak(stage.name)
        stage.spokenCueIds.filter { it.isNotBlank() }.forEach(::speak)
    }

    fun announceComplete(enabled: Boolean) {
        if (!enabled) return
        speak("Session complete")
    }

    private fun speak(text: String) {
        if (ready) enqueue(text) else pending += text
    }

    private fun enqueue(text: String) {
        runCatching { tts.speak(text, TextToSpeech.QUEUE_ADD, null, "cue-${System.nanoTime()}") }
    }
}
