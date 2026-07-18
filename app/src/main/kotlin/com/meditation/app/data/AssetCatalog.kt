package com.meditation.app.data

import android.content.Context
import com.meditation.core.GeneratorConfig
import kotlinx.serialization.Serializable

/**
 * DTOs for the bundled JSON manifests (assets/metadata/*.json). Kept separate from the domain model
 * so the on-disk manifest format (lowercase category/role strings, "filename" instead of a URI) can
 * evolve independently of Room and the engine.
 */
@Serializable
data class SoundSeedDto(
    val id: String,
    val name: String,
    val category: String,
    val sourceType: String,
    val filename: String? = null,
    val generatorConfig: GeneratorConfig? = null,
    val imageAssetId: String? = null,
    val durationMs: Long? = null,
    val defaultVolume: Double = 0.7,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val roles: List<String> = emptyList(),
    val attributionId: String? = null,
)

@Serializable
data class AttributionSeedDto(
    val id: String,
    val creator: String = "",
    val title: String = "",
    val sourceName: String = "",
    val sourcePage: String? = null,
    val licenseName: String = "",
    val licensePage: String? = null,
    val modifications: String? = null,
)

object AssetCatalog {

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    fun loadSounds(context: Context): List<SoundEntity> {
        val dtos: List<SoundSeedDto> =
            AppJson.decodeFromString(readAsset(context, "metadata/sounds.json"))
        return dtos.map { dto ->
            SoundEntity(
                id = dto.id,
                name = dto.name,
                category = dto.category.uppercase(),
                sourceType = dto.sourceType.uppercase(),
                // Bundled files resolve to a file:///android_asset URI so playback code has one path type.
                fileUri = dto.filename?.let { "file:///android_asset/audio/$it" },
                generatorConfigJson = dto.generatorConfig?.let {
                    AppJson.encodeToString(GeneratorConfig.serializer(), it)
                },
                imageAssetId = dto.imageAssetId,
                durationMs = dto.durationMs,
                defaultVolume = dto.defaultVolume,
                tagsJson = AppJson.encodeToString(dto.tags),
                description = dto.description,
                rolesJson = AppJson.encodeToString(dto.roles.map { it.uppercase() }),
                attributionId = dto.attributionId,
                favorite = false,
            )
        }
    }

    fun loadAttributions(context: Context): List<AttributionEntity> {
        val dtos: List<AttributionSeedDto> =
            AppJson.decodeFromString(readAsset(context, "metadata/attributions.json"))
        return dtos.map {
            AttributionEntity(
                id = it.id, creator = it.creator, title = it.title, sourceName = it.sourceName,
                sourcePage = it.sourcePage, licenseName = it.licenseName, licensePage = it.licensePage,
                modifications = it.modifications,
            )
        }
    }
}
