package com.meditation.app.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.meditation.app.data.SoundRepository
import com.meditation.core.ActiveSessionState
import com.meditation.core.SoundAsset
import com.meditation.core.SoundEvent
import com.meditation.core.SoundSourceType
import com.meditation.core.SessionSnapshot
import com.meditation.core.VolumeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val synth = ToneSynth() // procedural fallback when a strike sound has no bundled file
    private val fileExistsCache = HashMap<String, Boolean>()
    private val focus = AudioFocusManager(context)
    private val recorded = LinkedHashMap<String, AmbiencePlayer>() // soundId -> player, max 3
    private val generated = LinkedHashMap<String, NoiseGenerator>() // soundId -> generator
    private val pausedByFocus = mutableSetOf<String>() // generated layers stopped for a call
    @Volatile private var focusPaused = false // true while a call holds audio focus
    private val playerFactory = { AmbiencePlayer(context) }
    // ExoPlayer (preview + ambience) must be touched only on the main thread.
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var masterAmbience = 0.6f
    private var focusHeld = false

    // Preview playback is kept on entirely separate players so it can never disturb an active
    // session's audio (brief §11). previewMix supports the ambient mixer's multi-layer preview.
    private val previewExos = LinkedHashMap<String, ExoPlayer>()
    private val previewNoises = LinkedHashMap<String, NoiseGenerator>()
    private var configPreviewGen: NoiseGenerator? = null

    /**
     * True while a *continuous* preview (looping ambience or a generated layer) is playing. The
     * foreground service watches this so previewed audio survives leaving the app: without it the
     * process is frozen in the background and playback simply stops. One-shot strikes never set it,
     * so previewing a bell cannot leave an ongoing notification behind.
     */
    private val _previewActive = MutableStateFlow(false)
    val previewActive: StateFlow<Boolean> = _previewActive.asStateFlow()

    init {
        focus.onDuck = { recorded.values.forEach { it.duck() } }
        // Focus loss only pauses for a phone call (see AudioFocusManager); when it does, silence the
        // procedural layers too — otherwise noise/drones would keep streaming into the call.
        focus.onPause = {
            focusPaused = true
            recorded.values.forEach { it.pause() }
            generated.forEach { (id, gen) -> pausedByFocus.add(id); gen.stop() }
        }
        focus.onResume = {
            focusPaused = false
            recorded.values.forEach { it.resume() }
            pausedByFocus.forEach { id -> generated[id]?.start() }
            pausedByFocus.clear()
        }
    }

    /** Play the strike/interval/closing/final sounds the engine emitted. */
    suspend fun play(events: List<SoundEvent>, volumes: VolumeSettings) {
        for (event in events) {
            val id = event.soundId ?: continue
            val asset = sounds.byId(id) ?: continue
            val vol = (volumes.bellVolume * asset.defaultVolume).toFloat().coerceIn(0f, 1f)
            if (hasBundledFile(asset)) {
                bells.play(id, asset.fileUri, vol, event.strikeCount, event.strikeSpacingMs)
            } else {
                // No recording bundled: synthesize the strike so the bell is still audible.
                synth.strike(asset, vol, event.strikeCount, event.strikeSpacingMs)
            }
        }
    }

    /**
     * One-shot playback of a single sound of any length — used by the gong start button. Uses a
     * throwaway ExoPlayer (not the SoundPool strike path, which is size-limited) on the main thread,
     * and releases it when the sound finishes. Independent of any active session or preview.
     */
    suspend fun strikeOnce(soundId: String, volume: Float = 1f) {
        val asset = sounds.byId(soundId) ?: return
        val uri = asset.fileUri ?: run { synth.strike(asset, volume, 1, 0); return }
        withContext(Dispatchers.Main) {
            val exo = ExoPlayer.Builder(context).build()
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) runCatching { exo.release() }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    runCatching { exo.release() }
                }
            })
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.repeatMode = Player.REPEAT_MODE_OFF
            exo.volume = volume
            exo.prepare()
            exo.play()
        }
    }

    /** Whether a real audio file exists for this asset (bundled in assets/ or an imported file). */
    private fun hasBundledFile(asset: SoundAsset): Boolean {
        val uri = asset.fileUri ?: return false
        return fileExistsCache.getOrPut(uri) {
            when {
                uri.startsWith("file:///android_asset/") -> runCatching {
                    context.assets.open(uri.removePrefix("file:///android_asset/")).close(); true
                }.getOrDefault(false)
                else -> runCatching {
                    java.io.File(android.net.Uri.parse(uri).path ?: return@runCatching false).exists()
                }.getOrDefault(false)
            }
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

        // Treat a call-driven focus pause as paused, so a periodic sync can't restart audio mid-call.
        val paused = !state.running || focusPaused
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
        // Swap players without letting previewActive dip to false in between, so the service that
        // keeps background playback alive is never torn down mid-change.
        stopPreviewPlayers()
        startPreview(soundId, volume)
    }

    /** Preview a full ambience mix (up to three layers) without touching the active session. */
    suspend fun previewMix(layers: List<Pair<String, Double>>) {
        stopPreviewPlayers()
        layers.take(3).forEach { (id, vol) -> startPreview(id, vol) }
    }

    private suspend fun startPreview(soundId: String, volume: Double) {
        val asset = sounds.byId(soundId) ?: return
        val vol = volume.toFloat().coerceIn(0f, 1f)
        if (asset.sourceType == SoundSourceType.GENERATED && asset.generatorConfig != null) {
            previewNoises[soundId] = NoiseGenerator(asset.generatorConfig!!).apply {
                setGain(vol.toDouble()); start()
            }
            _previewActive.value = true // generated layers run until stopped
        } else if (!hasBundledFile(asset)) {
            // No recording: synthesize strike voices (bells/bowls/gongs/wood/chime). Ambience
            // recordings without a file stay silent (their generated counterparts do play).
            if (synth.canVoice(asset.category)) synth.strike(asset, vol, 1, 0)
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
            if (loop) _previewActive.value = true // a looping ambience runs until stopped
        }
    }

    /**
     * Live-preview a generator config before it's saved as a sound (the drone editor). Independent
     * of [previewSound] since there's no soundId yet to look up.
     */
    fun previewGeneratorConfig(config: com.meditation.core.GeneratorConfig, volume: Double) {
        configPreviewGen?.stop()
        configPreviewGen = NoiseGenerator(config).apply { setGain(volume); start() }
    }

    /**
     * Live-preview a not-yet-saved strike voice (the bowl/chime editor) by synthesizing it directly
     * from an in-memory [asset] — no Room row required.
     */
    fun previewSynthAsset(asset: SoundAsset, volume: Double) {
        if (synth.canVoice(asset.category)) synth.strike(asset, volume.toFloat().coerceIn(0f, 1f), 1, 0)
    }

    fun stopPreview() {
        stopPreviewPlayers()
        _previewActive.value = false
    }

    private fun stopPreviewPlayers() {
        val exos = previewExos.values.toList()
        previewExos.clear()
        if (exos.isNotEmpty()) mainScope.launch { exos.forEach { runCatching { it.release() } } }
        previewNoises.values.forEach { it.stop() }
        previewNoises.clear()
        configPreviewGen?.stop()
        configPreviewGen = null
    }

    fun stopAll() {
        recorded.values.forEach { it.stop() }
        recorded.clear()
        generated.values.forEach { it.stop() }
        generated.clear()
        pausedByFocus.clear()
        focusPaused = false
        if (focusHeld) { focus.abandon(); focusHeld = false }
    }

    fun release() {
        stopAll()
        stopPreview()
        recorded.values.forEach { it.release() }
        bells.release()
    }
}
