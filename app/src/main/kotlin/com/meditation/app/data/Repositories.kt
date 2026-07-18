package com.meditation.app.data

import android.content.Context
import com.meditation.core.ActiveSessionState
import com.meditation.core.Attribution
import com.meditation.core.CompletedSession
import com.meditation.core.SessionPreset
import com.meditation.core.SoundAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    /** Seed the bundled catalog on first launch; a no-op once populated. */
    suspend fun seedIfEmpty(context: Context) {
        if (dao.count() > 0) return
        attributionDao.upsertAll(AssetCatalog.loadAttributions(context))
        dao.upsertAll(AssetCatalog.loadSounds(context))
    }
}
