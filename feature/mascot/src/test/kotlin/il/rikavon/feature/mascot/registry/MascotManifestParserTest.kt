package il.rikavon.feature.mascot.registry

import il.rikavon.core.data.model.AchievementId
import il.rikavon.feature.mascot.model.HourBucket
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.model.ReactionPreset
import il.rikavon.feature.mascot.model.UnlockRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotManifestParserTest {
    private val parser = MascotManifestParser()

    private fun texts(count: Int = 8) = (1..count).joinToString(",") { "\"text $it\"" }

    private fun stage(key: Int, asset: String? = "stage_$key.json") =
        """
        "$key": { ${asset?.let { "\"asset\": \"$it\"," } ?: ""} "texts": { "he": [${texts()}], "en": [${texts()}] } }
        """.trimIndent()

    private fun manifest(
        id: String = "brain",
        unlock: String = "\"free\"",
        reaction: String = "pulse",
        color: String = "#FF7EB6",
        stages: String = MascotStage.entries.joinToString(",") { stage(it.key) },
        blockBucket: String = "[\"a\", \"b\"]",
    ) = """
        {
          "schemaVersion": 1,
          "id": "$id",
          "name": { "he": "מוח", "en": "Brain" },
          "themeColor": "$color",
          "personality": "cynical",
          "unlock": $unlock,
          "reaction": "$reaction",
          "sound": "reaction.wav",
          "stages": { $stages },
          "blockMessages": { "he": { "morning": $blockBucket, "day": $blockBucket, "evening": $blockBucket, "night": $blockBucket } },
          "summary": { "he": { "100": "a", "80": "b", "60": "c", "40": "d", "20": "e", "0": "f" } }
        }
        """.trimIndent()

    private val allAssetsExist: (String) -> Boolean = { true }

    @Test
    fun `parses a complete manifest`() {
        val skin = parser.parse("brain", manifest(), allAssetsExist)
        assertEquals("brain", skin.id)
        assertEquals("מוח", skin.name.resolve("he"))
        assertEquals("Brain", skin.name.resolve("en"))
        assertEquals(0xFFFF7EB6L, skin.themeColorArgb)
        assertEquals(UnlockRule.Free, skin.unlock)
        assertEquals(ReactionPreset.PULSE, skin.reaction)
        assertEquals("mascots/brain/reaction.wav", skin.soundAsset)
        assertEquals(MascotStage.entries.size, skin.stages.size)
        assertEquals("mascots/brain/stage_100.json", skin.stage(MascotStage.PRISTINE).lottieAsset)
        assertEquals(
            2,
            skin.blockMessages
                .resolve("he")
                ?.get(HourBucket.NIGHT)
                ?.size,
        )
        assertEquals("f", skin.summaries.resolve("he")?.get(MascotStage.ROTTEN))
    }

    @Test
    fun `achievement unlock rule is parsed`() {
        val skin = parser.parse("brain", manifest(unlock = "{\"achievement\": \"streak_7\"}"), allAssetsExist)
        assertEquals(UnlockRule.Achievement(AchievementId.STREAK_7), skin.unlock)
    }

    @Test
    fun `missing lottie asset falls back to null so the built-in animation is used`() {
        val skin = parser.parse("brain", manifest(), { it != "stage_0.json" })
        assertNull(skin.stage(MascotStage.ROTTEN).lottieAsset)
        assertEquals("mascots/brain/stage_20.json", skin.stage(MascotStage.ROTTING).lottieAsset)
    }

    @Test
    fun `id must match the folder name`() {
        assertThrows(MascotManifestException::class.java) {
            parser.parse("cat", manifest(id = "brain"), allAssetsExist)
        }
    }

    @Test
    fun `every stage is required`() {
        val stages = MascotStage.entries.filter { it != MascotStage.WORN }.joinToString(",") { stage(it.key) }
        assertThrows(MascotManifestException::class.java) {
            parser.parse("brain", manifest(stages = stages), allAssetsExist)
        }
    }

    @Test
    fun `each stage needs at least eight texts`() {
        val stages =
            MascotStage.entries.joinToString(",") {
                if (it == MascotStage.FRESH) {
                    "\"80\": { \"asset\": \"stage_80.json\", \"texts\": { \"he\": [${texts(3)}] } }"
                } else {
                    stage(it.key)
                }
            }
        assertThrows(MascotManifestException::class.java) {
            parser.parse("brain", manifest(stages = stages), allAssetsExist)
        }
    }

    @Test
    fun `unknown reaction and bad colour are rejected`() {
        assertThrows(MascotManifestException::class.java) {
            parser.parse("brain", manifest(reaction = "dance"), allAssetsExist)
        }
        assertThrows(MascotManifestException::class.java) {
            parser.parse("brain", manifest(color = "pink"), allAssetsExist)
        }
    }

    @Test
    fun `block messages need two per bucket`() {
        assertThrows(MascotManifestException::class.java) {
            parser.parse("brain", manifest(blockBucket = "[\"only\"]"), allAssetsExist)
        }
    }

    @Test
    fun `malformed json is reported with the folder name`() {
        val error =
            assertThrows(MascotManifestException::class.java) { parser.parse("brain", "{ not json", allAssetsExist) }
        assertTrue(error.message!!.startsWith("brain:"))
    }

    @Test
    fun `colour parsing accepts rgb and argb`() {
        assertEquals(0xFF112233L, MascotManifestParser.parseColor("#112233"))
        assertEquals(0x80112233L, MascotManifestParser.parseColor("80112233"))
        assertNull(MascotManifestParser.parseColor("#12"))
    }

    @Test
    fun `stage mapping from score matches the documented thresholds`() {
        assertEquals(MascotStage.PRISTINE, MascotStage.fromScore(90))
        assertEquals(MascotStage.FRESH, MascotStage.fromScore(89))
        assertEquals(MascotStage.WORN, MascotStage.fromScore(50))
        assertEquals(MascotStage.WILTED, MascotStage.fromScore(49))
        assertEquals(MascotStage.ROTTING, MascotStage.fromScore(10))
        assertEquals(MascotStage.ROTTEN, MascotStage.fromScore(9))
    }
}
