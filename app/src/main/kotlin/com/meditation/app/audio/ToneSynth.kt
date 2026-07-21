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
import kotlin.math.tanh

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
        val rand = kotlin.random.Random(v.baseHz.toRawBits())
        var transientLp = 0.0 // one-pole low-pass state for the strike transient
        for (n in 0 until total) {
            val t = n.toDouble() / sampleRate
            val attack = 1.0 - exp(-t / v.attackTau)
            var s = 0.0
            // Each partial has its OWN decay — higher/inharmonic partials fade first, like real metal.
            for (p in v.partials) s += p.amp * exp(-t / p.decayTau) * sin(2 * PI * v.baseHz * p.ratio * t)
            s *= attack
            // Short filtered-noise "strike" transient gives the attack a physical edge.
            if (v.transientAmt > 0.0 && t < 0.05) {
                transientLp += (rand.nextDouble(-1.0, 1.0) - transientLp) * 0.35
                s += transientLp * v.transientAmt * exp(-t / 0.008)
            }
            buf[n] = tanh(s * v.gain * volume).toFloat() // soft-clip, no harsh breakup
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

    /** A single mode: frequency ratio, starting amplitude, and its own decay time constant. */
    private class Partial(val ratio: Double, val amp: Double, val decayTau: Double)
    private class Voice(
        val baseHz: Double,
        val partials: List<Partial>,
        val attackTau: Double,
        val durationSec: Double,
        val gain: Double,
        val transientAmt: Double,
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
                partials = listOf(
                    Partial(1.0, 1.0, 2.4), Partial(2.0, 0.5, 1.5), Partial(2.76, 0.45, 0.9),
                    Partial(5.4, 0.25, 0.5), Partial(8.9, 0.12, 0.28),
                ),
                attackTau = 0.0015, durationSec = 3.6, gain = 0.5, transientAmt = 0.25,
            )
            SoundCategory.BOWL -> Voice(
                baseHz = 300.0 * toneFactor * jitter,
                partials = listOf(
                    Partial(1.0, 1.0, 5.5), Partial(1.004, 0.95, 5.5), // detuned pair → slow beating
                    Partial(2.01, 0.4, 3.5), Partial(2.98, 0.22, 2.2), Partial(4.2, 0.1, 1.2),
                ),
                attackTau = 0.03, durationSec = 7.0, gain = 0.46, transientAmt = 0.08,
            )
            SoundCategory.GONG -> Voice(
                baseHz = 150.0 * toneFactor * jitter,
                partials = listOf(
                    Partial(1.0, 1.0, 6.0), Partial(1.48, 0.7, 4.5), Partial(2.34, 0.5, 3.2),
                    Partial(3.86, 0.35, 2.0), Partial(5.12, 0.22, 1.3), Partial(7.24, 0.12, 0.8),
                ),
                attackTau = 0.02, durationSec = 8.0, gain = 0.44, transientAmt = 0.4,
            )
            SoundCategory.WOOD -> Voice(
                baseHz = 1100.0 * toneFactor * jitter,
                partials = listOf(Partial(1.0, 1.0, 0.05), Partial(3.1, 0.5, 0.03), Partial(6.0, 0.2, 0.02)),
                attackTau = 0.0008, durationSec = 0.3, gain = 0.7, transientAmt = 0.8,
            )
            else -> Voice( // soft completion bell
                baseHz = 560.0 * toneFactor * jitter,
                partials = listOf(Partial(1.0, 1.0, 2.4), Partial(2.0, 0.35, 1.4), Partial(3.0, 0.12, 0.8)),
                attackTau = 0.004, durationSec = 3.2, gain = 0.46, transientAmt = 0.1,
            )
        }
    }
}
