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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Looping playback of a single recorded ambience track via ExoPlayer with fade-in/out (brief §12,
 * backlog E3).
 *
 * ExoPlayer is not thread-safe: it must be created and every method call must happen on a thread
 * with a Looper. This class therefore confines ALL player interaction to the main dispatcher — the
 * player is created lazily on first use and every public method marshals its work onto [scope], so
 * callers may invoke these methods from any thread (the controller runs on Dispatchers.Default).
 */
@UnstableApi
class AmbiencePlayer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var player: ExoPlayer? = null
    private var targetVolume = 0.6f
    private var fadeJob: Job? = null
    private var currentUri: String? = null

    /** Must be called on the main thread (inside [scope]). */
    private fun obtainPlayer(): ExoPlayer =
        player ?: ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { /* absent/unsupported file: stay silent */ }
            })
        }.also { player = it }

    fun play(fileUri: String?, volume: Float, fadeInMs: Long = 1500) {
        scope.launch {
            if (fileUri == null) { stopNow(0); return@launch }
            targetVolume = volume
            val p = obtainPlayer()
            if (currentUri == fileUri && p.isPlaying) { fadeTo(volume, 400); return@launch }
            currentUri = fileUri
            runCatching {
                p.setMediaItem(MediaItem.fromUri(fileUri))
                p.volume = 0f
                p.prepare()
                p.play()
            }
            fadeTo(volume, fadeInMs)
        }
    }

    fun setVolume(volume: Float) = scope.launch { targetVolume = volume; fadeTo(volume, 300) }.let {}
    fun pause() = scope.launch { fadeJob?.cancel(); player?.pause() }.let {}
    fun resume() = scope.launch { player?.play(); fadeTo(targetVolume, 800) }.let {}
    fun duck() = scope.launch { fadeTo(targetVolume * 0.3f, 300) }.let {}
    fun unduck() = scope.launch { fadeTo(targetVolume, 500) }.let {}
    fun stop(fadeOutMs: Long = 1200) = scope.launch { stopNow(fadeOutMs) }.let {}
    fun release() = scope.launch { fadeJob?.cancel(); player?.release(); player = null }.let {}

    private suspend fun stopNow(fadeOutMs: Long) {
        val p = player ?: return
        fadeJob?.cancel()
        val start = p.volume
        if (fadeOutMs > 0) {
            val steps = 20
            repeat(steps) { i ->
                p.volume = start * (1f - (i + 1f) / steps)
                delay(fadeOutMs / steps)
            }
        }
        runCatching { p.stop() }
        currentUri = null
    }

    private fun fadeTo(volume: Float, durationMs: Long) {
        val p = player ?: return
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
}
