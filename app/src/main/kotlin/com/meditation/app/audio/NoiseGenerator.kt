package com.meditation.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.meditation.core.GeneratorConfig
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Procedurally generates continuous PCM audio (white/pink/brown noise, sine and dual drones) via
 * [AudioTrack], streaming natively so it continues with the screen off and never depends on the
 * backgrounded WebView-equivalent UI (brief §9.3). One instance drives one generated layer.
 */
class NoiseGenerator(private val config: GeneratorConfig) {

    private val sampleRate = 44_100
    @Volatile private var running = false
    @Volatile private var gain = config.gain.toFloat().coerceIn(0f, 1f)
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    fun setGain(value: Double) { gain = value.toFloat().coerceIn(0f, 1f) }

    fun start() {
        if (running) return
        running = true
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(4096)

        val at = AudioTrack.Builder()
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
        track = at
        at.play()

        worker = thread(name = "noise-${config.type}", isDaemon = true) {
            val buffer = FloatArray(minBuf / 4)
            // Filter/oscillator state kept across buffers so there are no seams between writes.
            var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
            var brown = 0.0
            var phase = 0.0
            var phase2 = 0.0
            val freq = config.frequencyHz ?: 110.0
            val freq2 = config.secondFrequencyHz ?: 165.0
            val mix = (config.mix ?: 0.5)
            while (running) {
                for (i in buffer.indices) {
                    val sample = when (config.type) {
                        "white" -> whiteSample()
                        "pink" -> {
                            val w = whiteSample().toDouble()
                            // Paul Kellet's pink-noise approximation.
                            b0 = 0.99886 * b0 + w * 0.0555179
                            b1 = 0.99332 * b1 + w * 0.0750759
                            b2 = 0.96900 * b2 + w * 0.1538520
                            b3 = 0.86650 * b3 + w * 0.3104856
                            b4 = 0.55000 * b4 + w * 0.5329522
                            b5 = -0.7616 * b5 - w * 0.0168980
                            val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
                            b6 = w * 0.115926
                            (pink * 0.11).toFloat()
                        }
                        "brown" -> {
                            // Leaky integrator (never clamps to a rail), then soft-limited below.
                            brown = brown * 0.996 + whiteSample() * 0.04
                            (brown * 2.2).toFloat()
                        }
                        "sine" -> {
                            phase += 2 * PI * freq / sampleRate
                            if (phase > 2 * PI) phase -= 2 * PI
                            sin(phase).toFloat()
                        }
                        "dual" -> {
                            phase += 2 * PI * freq / sampleRate
                            phase2 += 2 * PI * freq2 / sampleRate
                            if (phase > 2 * PI) phase -= 2 * PI
                            if (phase2 > 2 * PI) phase2 -= 2 * PI
                            ((1 - mix) * sin(phase) + mix * sin(phase2)).toFloat()
                        }
                        else -> whiteSample()
                    }
                    // tanh soft-clip: smoothly saturates instead of hard-clipping to the rails,
                    // which is what caused the brown-noise breakup.
                    buffer[i] = tanh((sample * gain).toDouble()).toFloat()
                }
                val t = track ?: break
                t.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    fun stop() {
        running = false
        worker?.join(200)
        worker = null
        runCatching { track?.pause(); track?.flush(); track?.stop() }
        runCatching { track?.release() }
        track = null
    }

    private fun whiteSample(): Float = (Random.nextFloat() * 2f - 1f)
}
