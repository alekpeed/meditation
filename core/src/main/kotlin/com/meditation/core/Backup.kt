package com.meditation.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A complete, portable snapshot of the user's local data (Phase 2 backup/restore). Everything stays
 * on-device; this is just a file the user can save and re-import. [preferencesJson] carries the
 * app's DataStore preferences opaquely so the core stays UI-agnostic.
 */
@Serializable
data class Backup(
    val version: Int = CURRENT_VERSION,
    val exportedWallMs: Long,
    val presets: List<SessionPreset> = emptyList(),
    val history: List<CompletedSession> = emptyList(),
    val favoriteSoundIds: List<String> = emptyList(),
    val preferencesJson: String? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

        fun encode(backup: Backup): String = json.encodeToString(backup)

        /** Decode a backup file. Returns null if it is not a recognizable/compatible backup. */
        fun decode(text: String): Backup? = runCatching {
            json.decodeFromString<Backup>(text)
        }.getOrNull()?.takeIf { it.version <= CURRENT_VERSION }
    }
}
