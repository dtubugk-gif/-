package il.rikavon.feature.mascot.registry

import il.rikavon.core.data.model.AchievementId
import il.rikavon.feature.mascot.model.HourBucket
import il.rikavon.feature.mascot.model.Localized
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.model.ReactionPreset
import il.rikavon.feature.mascot.model.StageSkin
import il.rikavon.feature.mascot.model.UnlockRule
import il.rikavon.feature.mascot.model.VoiceProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class ManifestDto(
    val schemaVersion: Int,
    val id: String,
    val name: Map<String, String>,
    val themeColor: String,
    val surfaceTint: String? = null,
    val personality: String,
    val unlock: JsonElement,
    val reaction: String,
    val sound: String? = null,
    val voice: VoiceDto? = null,
    val stages: Map<String, StageDto>,
    val blockMessages: Map<String, Map<String, List<String>>>,
    val summary: Map<String, Map<String, String>>,
)

@Serializable
internal data class StageDto(val asset: String? = null, val texts: Map<String, List<String>>)

@Serializable
internal data class VoiceDto(val pitch: Float = 1f, val rate: Float = 1f, val neural: String? = null)

class MascotManifestException(message: String) : IllegalArgumentException(message)

/**
 * Parses and validates `manifest.json`. Pure Kotlin so it is unit-testable without Android.
 *
 * @param folder the mascot folder name; must equal the manifest id.
 * @param assetExists resolves whether a file exists inside the mascot folder.
 */
class MascotManifestParser(private val json: Json = Json { ignoreUnknownKeys = true }) {
    fun parse(folder: String, manifestText: String, assetExists: (String) -> Boolean): MascotSkin {
        val dto =
            runCatching { json.decodeFromString(ManifestDto.serializer(), manifestText) }
                .getOrElse { throw MascotManifestException("$folder: malformed manifest: ${it.message}") }
        require(dto.schemaVersion == MascotSkin.SCHEMA_VERSION) {
            "$folder: unsupported schemaVersion ${dto.schemaVersion}"
        }
        require(dto.id == folder) { "$folder: manifest id '${dto.id}' must match folder name" }
        require(dto.name.values.any { it.isNotBlank() }) { "$folder: name is empty" }

        val color =
            parseColor(dto.themeColor) ?: throw MascotManifestException("$folder: bad themeColor '${dto.themeColor}'")
        val surfaceTint = parseSurfaceTint(folder, dto.surfaceTint)
        val reaction =
            ReactionPreset.fromKey(dto.reaction)
                ?: throw MascotManifestException("$folder: unknown reaction '${dto.reaction}'")
        val unlock = parseUnlock(folder, dto.unlock)

        val stages =
            MascotStage.entries.associateWith { stage ->
                val stageDto =
                    dto.stages[stage.key.toString()]
                        ?: throw MascotManifestException("$folder: missing stage ${stage.key}")
                val hasEnoughTexts = stageDto.texts.values.any { it.size >= MascotSkin.MIN_TEXTS_PER_STAGE }
                require(hasEnoughTexts) {
                    "$folder: stage ${stage.key} needs at least ${MascotSkin.MIN_TEXTS_PER_STAGE} texts in one language"
                }
                val asset = stageDto.asset?.takeIf { it.endsWith(LOTTIE_EXTENSION) && assetExists(it) }
                StageSkin(
                    stage = stage,
                    lottieAsset = asset?.let { "$ASSET_ROOT/$folder/$it" },
                    texts = Localized(stageDto.texts),
                )
            }

        val blockMessages =
            Localized(
                dto.blockMessages.mapValues { (_, buckets) ->
                    buckets.entries.mapNotNull { (key, list) -> HourBucket.fromKey(key)?.let { it to list } }.toMap()
                },
            )
        val hasBlockMessages =
            blockMessages.byLanguage.values.any { buckets ->
                HourBucket.entries.all { (buckets[it]?.size ?: 0) >= MascotSkin.MIN_BLOCK_MESSAGES_PER_BUCKET }
            }
        require(hasBlockMessages) {
            "$folder: blockMessages must cover morning/day/evening/night with " +
                "${MascotSkin.MIN_BLOCK_MESSAGES_PER_BUCKET}+ messages each"
        }

        val summaries =
            Localized(
                dto.summary.mapValues { (_, byStage) ->
                    byStage.entries
                        .mapNotNull { (key, text) ->
                            key.toIntOrNull()?.let(MascotStage::fromKey)?.let { it to text }
                        }.toMap()
                },
            )
        require(summaries.byLanguage.values.any { it.size == MascotStage.entries.size }) {
            "$folder: summary must have a text for every stage"
        }

        return MascotSkin(
            id = dto.id,
            name = Localized(dto.name),
            themeColorArgb = color,
            surfaceTintArgb = surfaceTint,
            personality = dto.personality,
            unlock = unlock,
            reaction = reaction,
            soundAsset = dto.sound?.takeIf(assetExists)?.let { "$ASSET_ROOT/$folder/$it" },
            voice =
                dto.voice?.let {
                    VoiceProfile.clamped(
                        it.pitch,
                        it.rate,
                        it.neural ?: VoiceProfile.forPersonality(dto.personality).neuralVoiceId,
                    )
                } ?: VoiceProfile.forPersonality(dto.personality),
            stages = stages,
            blockMessages = blockMessages,
            summaries = summaries,
        )
    }

    private fun parseSurfaceTint(folder: String, text: String?): Long? =
        text?.let { parseColor(it) ?: throw MascotManifestException("$folder: bad surfaceTint '$it'") }

    private fun parseUnlock(folder: String, element: JsonElement): UnlockRule {
        if (element is JsonPrimitive && element.contentOrNull == FREE) return UnlockRule.Free
        val obj =
            (element as? JsonObject)
                ?: throw MascotManifestException("$folder: unlock must be \"free\" or {\"achievement\": id}")
        val key =
            obj[ACHIEVEMENT]?.jsonPrimitive?.contentOrNull
                ?: throw MascotManifestException("$folder: unlock.achievement missing")
        val id = AchievementId.fromKey(key) ?: throw MascotManifestException("$folder: unknown achievement '$key'")
        return UnlockRule.Achievement(id)
    }

    private fun require(condition: Boolean, message: () -> String) {
        if (!condition) throw MascotManifestException(message())
    }

    companion object {
        const val ASSET_ROOT = "mascots"
        const val MANIFEST_FILE = "manifest.json"
        private const val LOTTIE_EXTENSION = ".json"
        private const val FREE = "free"
        private const val ACHIEVEMENT = "achievement"
        private const val RGB_LENGTH = 6
        private const val ARGB_LENGTH = 8
        private const val HEX_RADIX = 16
        private const val OPAQUE = 0xFF000000L

        fun parseColor(text: String): Long? {
            val hex = text.removePrefix("#")
            return when (hex.length) {
                RGB_LENGTH -> hex.toLongOrNull(HEX_RADIX)?.let { it or OPAQUE }
                ARGB_LENGTH -> hex.toLongOrNull(HEX_RADIX)
                else -> null
            }
        }
    }
}

internal fun JsonElement.asObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()
