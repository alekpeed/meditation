package com.meditation.app.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Looping playback of a single recorded ambience track via ExoPlayer with fade-in/out (brief §12,
 * backlog E3). Seamless looping is delegated to ExoPlayer's REPEAT_MODE_ONE; assets should already
 * be trimmed to loop cleanly per the asset manifest. Handles focus-driven pause/duck/resume.
 */
@OptIn(UnstableApi::class)
class AmbiencePlayer(context: Context, private val scope: CoroutineScope) {

    private val player = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ false, // focus is centralized in AudioFocusManager
        )
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { /* absent/unsupported file: stay silent */ }
        })
    }

    private var targetVolume = 0.6f
    private var fadeJob: Job? = null
    private var currentUri: String? = null

    fun play(fileUri: String?, volume: Float, fadeInMs: Long = 1500) {
        if (fileUri == null) { stop(0); return }
        targetVolume = volume
        if (currentUri == fileUri && player.isPlaying) {
            fadeTo(volume, 400); return
        }
        currentUri = fileUri
        runCatching {
            player.setMediaItem(MediaItem.fromUri(fileUri))
            player.volume = 0f
            player.prepare()
            player.play()
            fadeTo(volume, fadeInMs)
        }
    }

    fun setVolume(volume: Float) { targetVolume = volume; fadeTo(volume, 300) }

    fun pause() { fadeJob?.cancel(); player.pause() }
    fun resume() { player.play(); fadeTo(targetVolume, 800) }
    fun duck() { fadeTo(targetVolume * 0.3f, 300) }
    fun unduck() { fadeTo(targetVolume, 500) }

    fun stop(fadeOutMs: Long = 1200) {
        val p = player
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = 20
            val start = p.volume
            if (fadeOutMs > 0) {
                repeat(steps) { i ->
                    p.volume = start * (1f - (i + 1f) / steps)
                    delay(fadeOutMs / steps)
                }
            }
            p.stop()
            currentUri = null
        }
    }

    private fun fadeTo(volume: Float, durationMs: Long) {
        val p = player
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = 20
            val start = p.volume
            repeat(steps) { i ->
                p.volume = start + (volume - start) * (i + 1f) / steps
                delay(durationMs / steps)
            }
            p.volume = volume
        }
    }

    fun release() { fadeJob?.cancel(); player.release() }
}
