package com.meditation.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.meditation.core.Binaural
import com.meditation.core.GeneratorConfig
import com.meditation.core.NoiseColorCalibration
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

    // Binaural beats need a different tone per ear, so that one type streams stereo; everything
    // else stays mono (identical to before).
    private val stereo = config.type == "binaural"

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

            if (stereo) {
                streamBinaural(framesPerChunk)
                return@thread
            }

            val buffer = FloatArray(framesPerChunk)
            // Filter/oscillator state kept across buffers so there are no seams between writes.
            var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
            var brown = 0.0
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
                        "white" -> {
                            // Keep the advertised white noise genuinely full-band. Its gain is
                            // calibrated to the existing pink implementation's uncompressed RMS.
                            (whiteSample() * NoiseColorCalibration.whiteGain).toFloat()
                        }
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
                            // A calibrated leaky integrator: the 70 Hz shelf prevents the old
                            // sub-bass-heavy rumble while retaining brown's -6 dB/octave colour.
                            brown = brown * NoiseColorCalibration.BROWN_POLE +
                                whiteSample() * NoiseColorCalibration.brownDrive
                            brown.toFloat()
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
