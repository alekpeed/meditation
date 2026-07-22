package com.meditation.app.data

import android.content.Context
import com.meditation.core.ActiveSessionState
import com.meditation.core.Attribution
import com.meditation.core.CompletedSession
import com.meditation.core.SessionPreset
import com.meditation.core.SoundAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

class PresetRepository(private val dao: PresetDao) {
    val all: Flow<List<SessionPreset>> = dao.observeAll().map { it.map(PresetEntity::toDomain) }
    val favorites: Flow<List<SessionPreset>> = dao.observeFavorites().map { it.map(PresetEntity::toDomain) }
    suspend fun byId(id: String): SessionPreset? = dao.byId(id)?.toDomain()
    suspend fun save(preset: SessionPreset) = dao.upsert(preset.toEntity())
    suspend fun delete(id: String) = dao.delete(id)
}

/**
 * Owns the single persisted active-session row. Writes happen on every state transition so the
 * session can always be rebuilt after process death.
 */
class ActiveSessionRepository(private val dao: ActiveSessionDao) {
    val observe: Flow<ActiveSessionState?> = dao.observe().map { it?.toDomain() }
    suspend fun load(): ActiveSessionState? = dao.get()?.toDomain()
    suspend fun save(state: ActiveSessionState, expectedEndWallMs: Long?) =
        dao.put(state.toEntity(expectedEndWallMs))
    suspend fun clear() = dao.clear()
}

class HistoryRepository(private val dao: HistoryDao) {
    val all: Flow<List<CompletedSession>> = dao.observeAll().map { it.map(HistoryEntity::toDomain) }
    val count: Flow<Int> = dao.observeCount()
    val totalActiveMs: Flow<Long> = dao.observeTotalActiveMs()

    /** Returns true if a new record was written; false if one already existed (dedup by session id). */
    suspend fun record(session: CompletedSession): Boolean =
        dao.insertIfAbsent(session.toEntity()) != -1L

    suspend fun updateCompletionFields(id: String, note: String?, tags: List<String>, moodAfter: Int?) =
        dao.updateCompletionFields(id, note, AppJson.encodeToString(tags), moodAfter)

    suspend fun delete(id: String) = dao.delete(id)
}

class SoundRepository(private val dao: SoundDao, private val attributionDao: AttributionDao) {
    val all: Flow<List<SoundAsset>> = dao.observeAll().map { it.map(SoundEntity::toDomain) }
    fun byCategory(category: String): Flow<List<SoundAsset>> =
        dao.observeByCategory(category).map { it.map(SoundEntity::toDomain) }
    val attributions: Flow<List<Attribution>> =
        attributionDao.observeAll().map { it.map(AttributionEntity::toDomain) }

    suspend fun byId(id: String): SoundAsset? = dao.byId(id)?.toDomain()
    suspend fun attributionById(id: String): Attribution? = attributionDao.byId(id)?.toDomain()
    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    /**
     * Insert a user-authored procedural drone/ambience sound (the drone editor). Just another row
     * in the existing `sounds` table — no Room migration needed. Plays through the same
     * NoiseGenerator path as the bundled generated sounds since [config] is non-null.
     */
    suspend fun addCustomDrone(
        id: String,
        name: String,
        category: com.meditation.core.SoundCategory,
        config: com.meditation.core.GeneratorConfig,
    ) {
        dao.upsertAll(listOf(
            SoundEntity(
                id = id, name = name, category = category.name, sourceType = "GENERATED",
                fileUri = null,
                generatorConfigJson = AppJson.encodeToString(com.meditation.core.GeneratorConfig.serializer(), config),
                imageAssetId = null, durationMs = null, defaultVolume = config.gain,
                tagsJson = AppJson.encodeToString(listOf("custom")),
                description = "Custom drone",
                rolesJson = AppJson.encodeToString(listOf(com.meditation.core.SoundRole.AMBIENCE.name)),
                attributionId = null, favorite = false,
            ),
        ))
    }

    /**
     * Insert a user-authored strike voice (the bowl/chime editor): a category plus descriptive
     * [tags] that [com.meditation.app.audio.ToneSynth]'s existing tag inference already understands
     * (bright/deep/warm/soft/airy/clear/high/low). No fileUri and no generatorConfig, so it plays
     * through the same synthesized-strike fallback every other tagless bundled sound uses.
     */
    suspend fun addCustomStrikeVoice(
        id: String,
        name: String,
        category: com.meditation.core.SoundCategory,
        tags: List<String>,
    ) {
        dao.upsertAll(listOf(
            SoundEntity(
                id = id, name = name, category = category.name, sourceType = "GENERATED",
                fileUri = null, generatorConfigJson = null,
                imageAssetId = null, durationMs = null, defaultVolume = 0.8,
                tagsJson = AppJson.encodeToString(tags),
                description = "Custom synthesized voice",
                rolesJson = AppJson.encodeToString(
                    listOf(
                        com.meditation.core.SoundRole.OPENING.name,
                        com.meditation.core.SoundRole.INTERVAL.name,
                        com.meditation.core.SoundRole.CLOSING.name,
                    ),
                ),
                attributionId = null, favorite = false,
            ),
        ))
    }

    /** Seed the bundled catalog on first launch; a no-op once populated. */
    suspend fun seedIfEmpty(context: Context) {
        if (dao.count() > 0) return
        attributionDao.upsertAll(AssetCatalog.loadAttributions(context))
        dao.upsertAll(AssetCatalog.loadSounds(context))
    }

    /**
     * Insert any catalog sounds/attributions not already present, without touching existing rows
     * (so user favorites survive). This is how new bundled/generated sounds — e.g. binaural beats
     * added in an update — reach installs that were seeded before the entry existed.
     */
    suspend fun seedMissing(context: Context) {
        val newSounds = AssetCatalog.loadSounds(context).filter { dao.byId(it.id) == null }
        if (newSounds.isNotEmpty()) dao.upsertAll(newSounds)
        val newAttrs = AssetCatalog.loadAttributions(context).filter { attributionDao.byId(it.id) == null }
        if (newAttrs.isNotEmpty()) attributionDao.upsertAll(newAttrs)
    }

    /**
     * Copy a user-selected audio file into app-private storage and register it as an imported sound.
     * The file is copied (not merely referenced) so it survives even if the original URI is revoked;
     * nothing is ever uploaded (brief §9.4, §22). Returns the new sound id, or null on failure.
     */
    suspend fun importFromUri(context: Context, uri: android.net.Uri, displayName: String): String? {
        return try {
            val dir = java.io.File(context.filesDir, "imported").apply { mkdirs() }
            val id = "imported-${java.util.UUID.randomUUID()}"
            val target = java.io.File(dir, "$id.audio")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dao.upsertAll(
                listOf(
                    SoundEntity(
                        id = id,
                        name = displayName.ifBlank { "Imported sound" },
                        category = "IMPORTED",
                        sourceType = "IMPORTED",
                        fileUri = android.net.Uri.fromFile(target).toString(),
                        generatorConfigJson = null,
                        imageAssetId = null,
                        durationMs = null,
                        defaultVolume = 0.8,
                        tagsJson = "[]",
                        description = "Imported by you.",
                        rolesJson = "[\"OPENING\",\"INTERVAL\",\"CLOSING\",\"AMBIENCE\"]",
                        attributionId = null,
                        favorite = false,
                    ),
                ),
            )
            id
        } catch (e: Exception) {
            null
        }
    }
}
