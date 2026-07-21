package com.meditation.app.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.meditation.app.data.SoundRepository
import com.meditation.core.ActiveSessionState
import com.meditation.core.SoundEvent
import com.meditation.core.SoundSourceType
import com.meditation.core.SessionSnapshot
import com.meditation.core.VolumeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Facade over all playback: short strikes ([BellPlayer]), recorded ambience loops ([AmbiencePlayer]),
 * procedural layers ([NoiseGenerator]), and centralized [AudioFocusManager]. Bell and ambience
 * volumes are independent (brief §12). Up to three ambience layers may play at once.
 *
 * This class is deliberately resilient: any missing asset degrades to silence rather than crashing,
 * so a session's timing (the actual product) is never compromised by an audio problem.
 */
@OptIn(UnstableApi::class)
class AudioController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sounds: SoundRepository,
) {
    private val bells = BellPlayer(context, scope)
    private val focus = AudioFocusManager(context)
    private val recorded = LinkedHashMap<String, AmbiencePlayer>() // soundId -> player, max 3
    private val generated = LinkedHashMap<String, NoiseGenerator>() // soundId -> generator
    private val playerFactory = { AmbiencePlayer(context) }
    // ExoPlayer (preview + ambience) must be touched only on the main thread.
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var masterAmbience = 0.6f
    private var focusHeld = false

    // Preview playback is kept on entirely separate players so it can never disturb an active
    // session's audio (brief §11). previewMix supports the ambient mixer's multi-layer preview.
    private val previewExos = LinkedHashMap<String, ExoPlayer>()
    private val previewNoises = LinkedHashMap<String, NoiseGenerator>()

    init {
        focus.onDuck = { recorded.values.forEach { it.duck() } }
        focus.onPause = { recorded.values.forEach { it.pause() } }
        focus.onResume = { recorded.values.forEach { it.resume() } }
    }

    /** Play the strike/interval/closing/final sounds the engine emitted. */
    suspend fun play(events: List<SoundEvent>, volumes: VolumeSettings) {
        for (event in events) {
            val id = event.soundId ?: continue
            val asset = sounds.byId(id) ?: continue
            val vol = (volumes.bellVolume * asset.defaultVolume).toFloat().coerceIn(0f, 1f)
            bells.play(id, asset.fileUri, vol, event.strikeCount, event.strikeSpacingMs)
        }
    }

    /** Keep ambience layers in sync with the current stage and running state. */
    suspend fun syncForState(state: ActiveSessionState, snapshot: SessionSnapshot, volumes: VolumeSettings) {
        masterAmbience = volumes.ambienceVolume.toFloat()

        val stageIndex = snapshot.stageIndex
        val desired = state.preset.stages.getOrNull(stageIndex)?.ambienceLayers.orEmpty().take(3)
        val desiredIds = desired.map { it.soundId }.toSet()

        if (!state.isActive) { stopAll(); return }

        // Remove layers no longer wanted.
        (recorded.keys - desiredIds).toList().forEach { recorded.remove(it)?.stop() }
        (generated.keys - desiredIds).toList().forEach { generated.remove(it)?.stop() }

        if (desired.isEmpty()) {
            if (focusHeld) { focus.abandon(); focusHeld = false }
            return
        }
        if (!focusHeld) { focus.requestFocus(); focusHeld = true }

        val paused = !state.running
        for (layer in desired) {
            val asset = sounds.byId(layer.soundId) ?: continue
            val layerVol = (masterAmbience * layer.volume).toFloat().coerceIn(0f, 1f)
            if (asset.sourceType == SoundSourceType.GENERATED && asset.generatorConfig != null) {
                val gen = generated.getOrPut(layer.soundId) { NoiseGenerator(asset.generatorConfig!!) }
                gen.setGain(layerVol.toDouble())
                if (!paused) gen.start() else gen.stop()
            } else {
                val player = recorded.getOrPut(layer.soundId) { playerFactory() }
                if (paused) player.pause() else player.play(asset.fileUri, layerVol)
            }
        }
    }

    // ---- Preview (isolated from session audio) --------------------------------------------

    /** Preview one sound. Short strikes play once; ambience/generated loop until [stopPreview]. */
    suspend fun previewSound(soundId: String, volume: Double) {
        stopPreview()
        startPreview(soundId, volume)
    }

    /** Preview a full ambience mix (up to three layers) without touching the active session. */
    suspend fun previewMix(layers: List<Pair<String, Double>>) {
        stopPreview()
        layers.take(3).forEach { (id, vol) -> startPreview(id, vol) }
    }

    private suspend fun startPreview(soundId: String, volume: Double) {
        val asset = sounds.byId(soundId) ?: return
        val vol = volume.toFloat().coerceIn(0f, 1f)
        if (asset.sourceType == SoundSourceType.GENERATED && asset.generatorConfig != null) {
            previewNoises[soundId] = NoiseGenerator(asset.generatorConfig!!).apply {
                setGain(vol.toDouble()); start()
            }
        } else {
            val uri = asset.fileUri ?: return
            val loop = !(asset.durationMs != null && asset.durationMs!! < 4000)
            // ExoPlayer must be created and controlled on the main thread.
            withContext(Dispatchers.Main) {
                val exo = ExoPlayer.Builder(context).build().apply {
                    repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    setMediaItem(MediaItem.fromUri(uri))
                    this.volume = vol // qualify: the function's `volume: Double` param shadows it otherwise
                    prepare(); play()
                }
                previewExos[soundId] = exo
            }
        }
    }

    fun stopPreview() {
        val exos = previewExos.values.toList()
        previewExos.clear()
        if (exos.isNotEmpty()) mainScope.launch { exos.forEach { runCatching { it.release() } } }
        previewNoises.values.forEach { it.stop() }
        previewNoises.clear()
    }

    fun stopAll() {
        recorded.values.forEach { it.stop() }
        recorded.clear()
        generated.values.forEach { it.stop() }
        generated.clear()
        if (focusHeld) { focus.abandon(); focusHeld = false }
    }

    fun release() {
        stopAll()
        stopPreview()
        recorded.values.forEach { it.release() }
        bells.release()
    }
}
