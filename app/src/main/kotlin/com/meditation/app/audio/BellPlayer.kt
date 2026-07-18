package com.meditation.app.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Low-latency playback of short bell/bowl/gong/wood strikes via [SoundPool] (brief §3). Files are
 * loaded lazily and cached by sound id. Missing files are logged and skipped so a session never
 * crashes on an absent asset (error-state resilience). Multi-strike completion patterns are handled
 * with spaced replays.
 */
class BellPlayer(private val context: Context, private val scope: CoroutineScope) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val idToSample = HashMap<String, Int>()
    private val loaded = HashSet<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
        }
    }

    /** Play [strikes] strikes of [soundId], spaced by [spacingMs], at [volume] (0..1). */
    fun play(soundId: String?, fileUri: String?, volume: Float, strikes: Int, spacingMs: Long) {
        if (soundId == null || fileUri == null) return
        val sample = sampleFor(soundId, fileUri) ?: return
        scope.launch {
            repeat(strikes.coerceAtLeast(1)) { index ->
                if (index > 0) delay(spacingMs.coerceAtLeast(0))
                playWhenReady(sample, volume)
            }
        }
    }

    private suspend fun playWhenReady(sample: Int, volume: Float) {
        // SoundPool loads asynchronously; wait briefly for the first play after cold load.
        var waited = 0
        while (sample !in loaded && waited < 1500) { delay(50); waited += 50 }
        soundPool.play(sample, volume, volume, 1, 0, 1f)
    }

    private fun sampleFor(soundId: String, fileUri: String): Int? {
        idToSample[soundId]?.let { return it }
        return try {
            val sample = when {
                fileUri.startsWith("file:///android_asset/") -> {
                    val assetPath = fileUri.removePrefix("file:///android_asset/")
                    val afd: AssetFileDescriptor = context.assets.openFd(assetPath)
                    soundPool.load(afd, 1).also { afd.close() }
                }
                else -> {
                    val path = Uri.parse(fileUri).path ?: return null
                    soundPool.load(path, 1)
                }
            }
            idToSample[soundId] = sample
            sample
        } catch (e: Exception) {
            Log.w(TAG, "Bell asset missing or unreadable: $fileUri", e)
            null
        }
    }

    fun release() {
        soundPool.release()
        idToSample.clear()
        loaded.clear()
    }

    companion object { private const val TAG = "BellPlayer" }
}
