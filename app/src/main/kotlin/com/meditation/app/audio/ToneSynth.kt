package com.meditation.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.meditation.core.SoundAsset
import com.meditation.core.SoundCategory
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedurally synthesizes short "strike" sounds — bells, bowls, gongs, wood, chimes — via
 * [AudioTrack], so the app is fully audible without any bundled recordings. Each sound id maps to a
 * stable timbre (base frequency nudged by category, tone tags, and an id hash) so different bells
 * sound distinct. This is a pleasant default, not a replacement for real recordings: once real WAVs
 * are added to assets/audio the file path takes precedence and synthesis is bypassed.
 */
class ToneSynth {

    private val sampleRate = 44_100

    fun strike(asset: SoundAsset, volume: Float, strikes: Int, spacingMs: Long) {
        val voice = voiceFor(asset)
        thread(isDaemon = true, name = "tone-${asset.id}") {
            repeat(strikes.coerceAtLeast(1)) { i ->
                if (i > 0) Thread.sleep(spacingMs.coerceAtLeast(0))
                runCatching { renderAndPlay(voice, volume) }
            }
        }
    }

    /** True for categories this synth voices; ambience/noise/drone are handled elsewhere. */
    fun canVoice(category: SoundCategory): Boolean = when (category) {
        SoundCategory.BELL, SoundCategory.BOWL, SoundCategory.GONG,
        SoundCategory.WOOD, SoundCategory.CHIME -> true
        else -> false
    }

    private fun renderAndPlay(v: Voice, volume: Float) {
        val total = (v.durationSec * sampleRate).toInt().coerceAtLeast(1)
        val buf = FloatArray(total)
        for (n in 0 until total) {
            val t = n.toDouble() / sampleRate
            val env = exp(-t / v.decayTau) * (1.0 - exp(-t / v.attackTau))
            var s = 0.0
            for (p in v.partials) s += p.amp * sin(2 * PI * v.baseHz * p.ratio * t)
            var x = s * env * v.gain * volume
            if (x > 1.0) x = 1.0 else if (x < -1.0) x = -1.0
            buf[n] = x.toFloat()
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(8192)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        runCatching {
            track.play()
            // Blocking writes pace to real-time playback, so the loop takes ~durationSec.
            var off = 0
            val chunk = 2048
            while (off < buf.size) {
                val w = track.write(buf, off, minOf(chunk, buf.size - off), AudioTrack.WRITE_BLOCKING)
                if (w <= 0) break
                off += w
            }
            Thread.sleep(120) // let the last buffer drain
            track.stop()
        }
        runCatching { track.release() }
    }

    // ---- Voice mapping --------------------------------------------------------------------

    private class Partial(val ratio: Double, val amp: Double)
    private class Voice(
        val baseHz: Double,
        val partials: List<Partial>,
        val attackTau: Double,
        val decayTau: Double,
        val durationSec: Double,
        val gain: Double,
    )

    private fun voiceFor(asset: SoundAsset): Voice {
        val tags = asset.tags.map { it.lowercase() }
        // A small, stable jitter per sound id so sounds in the same category differ.
        val jitter = 1.0 + ((asset.id.hashCode() and 0xFFFF) / 65535.0 - 0.5) * 0.18
        var toneFactor = 1.0
        if ("deep" in tags || "low" in tags || "warm" in tags) toneFactor *= 0.72
        if ("bright" in tags || "clear" in tags || "light" in tags || "airy" in tags || "high" in tags) toneFactor *= 1.32
        if ("soft" in tags) toneFactor *= 0.9

        return when (asset.category) {
            SoundCategory.BELL, SoundCategory.CHIME -> Voice(
                baseHz = 660.0 * toneFactor * jitter,
                partials = listOf(Partial(1.0, 1.0), Partial(2.0, 0.55), Partial(2.76, 0.4), Partial(5.4, 0.22)),
                attackTau = 0.002, decayTau = 1.9, durationSec = 3.4, gain = 0.55,
            )
            SoundCategory.BOWL -> Voice(
                baseHz = 300.0 * toneFactor * jitter,
                partials = listOf(Partial(1.0, 1.0), Partial(1.004, 0.9), Partial(2.01, 0.45), Partial(2.98, 0.28)),
                attackTau = 0.02, decayTau = 4.6, durationSec = 6.5, gain = 0.5,
            )
            SoundCategory.GONG -> Voice(
                baseHz = 150.0 * toneFactor * jitter,
                partials = listOf(Partial(1.0, 1.0), Partial(1.48, 0.7), Partial(2.34, 0.5), Partial(3.86, 0.32), Partial(5.12, 0.2)),
                attackTau = 0.03, decayTau = 5.5, durationSec = 7.5, gain = 0.5,
            )
            SoundCategory.WOOD -> Voice(
                baseHz = 1100.0 * toneFactor * jitter,
                partials = listOf(Partial(1.0, 1.0), Partial(3.1, 0.4)),
                attackTau = 0.001, decayTau = 0.06, durationSec = 0.25, gain = 0.7,
            )
            else -> Voice( // soft completion bell
                baseHz = 560.0 * toneFactor * jitter,
                partials = listOf(Partial(1.0, 1.0), Partial(2.0, 0.4)),
                attackTau = 0.004, decayTau = 2.2, durationSec = 3.0, gain = 0.5,
            )
        }
    }
}
