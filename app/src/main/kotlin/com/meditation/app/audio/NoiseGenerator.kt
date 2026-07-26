package com.meditation.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.meditation.core.Binaural
import com.meditation.core.GeneratorConfig
import com.meditation.core.NoiseColorCalibration
import com.meditation.core.PeakLimiter
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Procedurally generates continuous PCM audio (white/pink/brown noise, sine and dual drones) via
 * [AudioTrack], streaming natively so it continues with the screen off and never depends on the
 * backgrounded WebView-equivalent UI (brief §9.3). One instance drives one generated layer.
 */
class NoiseGenerator(private val config: GeneratorConfig) {

    // 48 kHz is the native rate on essentially all Android audio hardware, so there is no
    // resampling on the way out, and it is the rate the loudness calibration is defined at.
    private val sampleRate = NoiseColorCalibration.SAMPLE_RATE_HZ.toInt()
    @Volatile private var running = false
    @Volatile private var gain = config.gain.toFloat().coerceIn(0f, 1f)
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    fun setGain(value: Double) { gain = value.toFloat().coerceIn(0f, 1f) }

    private val isNoiseColour = NoiseColorCalibration.isNoiseColour(config.type)

    // Binaural needs a different tone per ear. The noise colours are stereo too, generated from two
    // *independent* random streams: decorrelated noise is what makes them sound wide and enveloping
    // rather than collapsing to a point inside the head. Drones stay mono.
    private val stereo = config.type == "binaural" || isNoiseColour

    fun start() {
        if (running) return
        running = true
        val channelMask = if (stereo) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val channels = if (stereo) 2 else 1
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, channelMask, AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(4096)
        // Give the track several buffers of headroom so a GC pause or scheduling hiccup can't
        // starve it mid-stream — a shallow (minimum-size) buffer is what makes streamed noise
        // click and "break up". We generate in small chunks but keep the pipe deep.
        val trackBytes = (minBuf * 4).coerceAtLeast(32_768)
        val framesPerChunk = (minBuf / 4 / channels).coerceAtLeast(1024)

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
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(trackBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = at
        at.play()

        worker = thread(name = "noise-${config.type}", isDaemon = true) {
            // Run at audio priority so the OS won't preempt us and underrun the track.
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            if (isNoiseColour) {
                streamNoise(framesPerChunk)
                return@thread
            }
            if (stereo) {
                streamBinaural(framesPerChunk)
                return@thread
            }

            val buffer = FloatArray(framesPerChunk)
            // Oscillator state kept across buffers so there are no seams between writes.
            var phase = 0.0
            var phase2 = 0.0
            val freq = config.frequencyHz ?: 110.0
            val freq2 = config.secondFrequencyHz ?: 165.0
            val mix = (config.mix ?: 0.5)
            // "generative" state: a small ensemble of detuned tones whose pitch and level each
            // drift toward a fresh random target every few seconds (smoothed, never a jump), so the
            // pad slowly evolves and never audibly loops.
            val genRoot = config.frequencyHz ?: 110.0
            val genRatios = doubleArrayOf(1.0, 1.5, 2.0, 0.75)
            val genPhase = DoubleArray(genRatios.size)
            val genFreqOffset = DoubleArray(genRatios.size)
            val genFreqTarget = DoubleArray(genRatios.size)
            val genAmp = DoubleArray(genRatios.size) { 0.5 }
            val genAmpTarget = DoubleArray(genRatios.size) { 0.5 }
            val genCountdown = IntArray(genRatios.size) { (sampleRate * 2) }
            val genRandom = kotlin.random.Random(System.nanoTime())
            while (running) {
                for (i in buffer.indices) {
                    val sample = when (config.type) {
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
                        "generative" -> {
                            var mixed = 0.0
                            for (v in genRatios.indices) {
                                if (genCountdown[v] <= 0) {
                                    genFreqTarget[v] = genRandom.nextDouble(-4.0, 4.0)
                                    genAmpTarget[v] = genRandom.nextDouble(0.25, 1.0)
                                    genCountdown[v] = sampleRate * genRandom.nextInt(3, 9)
                                }
                                genCountdown[v]--
                                // Exponential smoothing toward the current target — an inaudibly
                                // slow glide, never a step, so nothing sounds like a "note change".
                                genFreqOffset[v] += (genFreqTarget[v] - genFreqOffset[v]) * 0.000004
                                genAmp[v] += (genAmpTarget[v] - genAmp[v]) * 0.000004
                                val voiceFreq = genRoot * genRatios[v] + genFreqOffset[v]
                                genPhase[v] += 2 * PI * voiceFreq / sampleRate
                                if (genPhase[v] > 2 * PI) genPhase[v] -= 2 * PI
                                mixed += sin(genPhase[v]) * genAmp[v]
                            }
                            (mixed / genRatios.size).toFloat()
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

    /**
     * Streams a noise colour as decorrelated stereo through the calibrated chain in :core
     * (colour -> 30 Hz high-pass -> 18 kHz low-pass -> loudness-matched gain), with a peak limiter
     * linked across both channels so gain reduction never shifts the stereo image.
     */
    private fun streamNoise(framesPerChunk: Int) {
        val left = NoiseColorCalibration.channel(config.type, Random(System.nanoTime()))
        val right = NoiseColorCalibration.channel(config.type, Random(System.nanoTime() * 31 + 17))
        val limiter = PeakLimiter(sampleRate = sampleRate.toDouble())
        val buffer = FloatArray(framesPerChunk * 2)
        while (running) {
            val volume = gain
            var j = 0
            for (f in 0 until framesPerChunk) {
                val l = left.next() * volume
                val r = right.next() * volume
                val reduction = limiter.gainFor(maxOf(abs(l), abs(r)))
                buffer[j++] = (l * reduction).toFloat()
                buffer[j++] = (r * reduction).toFloat()
            }
            val t = track ?: break
            t.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    /** Streams two pure tones — one per ear — whose difference is the binaural beat. */
    private fun streamBinaural(framesPerChunk: Int) {
        val (fL, fR) = Binaural.earFrequencies(
            carrierHz = config.frequencyHz ?: 200.0,
            beatHz = config.secondFrequencyHz ?: 6.0,
        )
        val incL = 2 * PI * fL / sampleRate
        val incR = 2 * PI * fR / sampleRate
        val buffer = FloatArray(framesPerChunk * 2)
        var phL = 0.0
        var phR = 0.0
        while (running) {
            var j = 0
            for (f in 0 until framesPerChunk) {
                phL += incL; if (phL > 2 * PI) phL -= 2 * PI
                phR += incR; if (phR > 2 * PI) phR -= 2 * PI
                buffer[j++] = tanh(sin(phL) * gain).toFloat()
                buffer[j++] = tanh(sin(phR) * gain).toFloat()
            }
            val t = track ?: break
            t.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun whiteSample(): Float = (Random.nextFloat() * 2f - 1f)
}
