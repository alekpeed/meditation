package com.meditation.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Requests and observes audio focus (brief §13). We route through USAGE_MEDIA so headphone removal
 * pauses rather than blasting the speaker (acceptance #11, #12).
 *
 * Focus is cooperative: Android cannot give one app priority, but the app that loses focus decides
 * how to react. A meditation ambience is meant to run underneath whatever else is happening, so by
 * default this keeps playing through focus loss from other media apps (Instagram, a web video, a
 * music app) instead of pausing — the two streams simply mix.
 *
 * The one exception is a phone call: mixing ambience into a call is genuinely bad, so an active or
 * ringing call still pauses playback and it resumes when focus returns. Audio mode can lag the focus
 * callback slightly, so the call check is repeated shortly after the loss.
 */
class AudioFocusManager(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())

    var onDuck: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null

    /** When true, only phone calls pause playback; other apps taking focus are ignored. */
    var keepPlayingOverOtherApps = true

    private fun callActive(): Boolean = when (audioManager.mode) {
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE -> true
        else -> false
    }

    private fun handleLoss() {
        if (!keepPlayingOverOtherApps || callActive()) {
            onPause?.invoke()
            return
        }
        // The telephony audio mode is sometimes set just after focus is taken; re-check once.
        handler.postDelayed({ if (callActive()) onPause?.invoke() }, 700)
    }

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> handleLoss()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> handleLoss()
            // A transient duck (notification chirp) is brief; hold full volume rather than dipping.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                if (!keepPlayingOverOtherApps) onDuck?.invoke()
            AudioManager.AUDIOFOCUS_GAIN -> onResume?.invoke()
        }
    }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(listener)
                .build()
            request = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request?.let { audioManager.abandonAudioFocusRequest(it) }
            request = null
        } else {
            @Suppress("DEPRECATION") audioManager.abandonAudioFocus(listener)
        }
    }
}
