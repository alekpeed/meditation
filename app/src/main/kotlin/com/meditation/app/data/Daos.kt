package com.meditation.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE favorite = 1 ORDER BY updatedAt DESC LIMIT 4")
    fun observeFavorites(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun byId(id: String): PresetEntity?

    @Upsert
    suspend fun upsert(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ActiveSessionDao {
    @Query("SELECT * FROM active_session WHERE id = 1")
    fun observe(): Flow<ActiveSessionEntity?>

    @Query("SELECT * FROM active_session WHERE id = 1")
    suspend fun get(): ActiveSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ActiveSessionEntity)

    @Query("DELETE FROM active_session")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY startedWallMs DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE sessionId = :id")
    suspend fun byId(id: String): HistoryEntity?

    /** IGNORE on conflict guarantees a completed session creates exactly one record (acceptance #9). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: HistoryEntity): Long

    @Query("UPDATE history SET note = :note, tagsJson = :tagsJson, moodAfter = :moodAfter WHERE sessionId = :id")
    suspend fun updateCompletionFields(id: String, note: String?, tagsJson: String, moodAfter: Int?)

    @Query("DELETE FROM history WHERE sessionId = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM history WHERE completionStatus != 'CANCELLED'")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(actualActiveDurationMs),0) FROM history WHERE completionStatus != 'CANCELLED'")
    fun observeTotalActiveMs(): Flow<Long>
}

@Dao
interface SoundDao {
    @Query("SELECT * FROM sounds ORDER BY name")
    fun observeAll(): Flow<List<SoundEntity>>

    @Query("SELECT * FROM sounds WHERE category = :category ORDER BY name")
    fun observeByCategory(category: String): Flow<List<SoundEntity>>

    @Query("SELECT * FROM sounds WHERE id = :id")
    suspend fun byId(id: String): SoundEntity?

    @Upsert
    suspend fun upsertAll(sounds: List<SoundEntity>)

    @Query("UPDATE sounds SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM sounds")
    suspend fun count(): Int
}

@Dao
interface AttributionDao {
    @Query("SELECT * FROM attributions ORDER BY id")
    fun observeAll(): Flow<List<AttributionEntity>>

    @Query("SELECT * FROM attributions WHERE id = :id")
    suspend fun byId(id: String): AttributionEntity?

    @Upsert
    suspend fun upsertAll(items: List<AttributionEntity>)
}
