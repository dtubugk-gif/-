package il.rikavon.feature.mascot.registry

import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parses the real mascot folders shipped in assets exactly the way [MascotRegistry] does at runtime.
 * A new folder dropped into `assets/mascots` is validated here before it ever reaches a device.
 */
class BuiltInMascotsTest {
    private val root = File("src/main/assets/mascots")
    private val parser = MascotManifestParser()

    private fun folders(): List<File> = root.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }

    private fun parse(folder: File): MascotSkin {
        val files = folder.list().orEmpty().toSet()
        val manifest = File(folder, MascotManifestParser.MANIFEST_FILE).readText()
        return parser.parse(folder.name, manifest) { it in files }
    }

    @Test
    fun `six mascots ship and all parse`() {
        val folders = folders()
        assertEquals(listOf("brain", "cat", "goldfish", "plant", "potato", "robot"), folders.map { it.name })
        folders.forEach { parse(it) }
    }

    @Test
    fun `every stage of every mascot has a lottie file and a reaction sound`() {
        folders().forEach { folder ->
            val skin = parse(folder)
            MascotStage.entries.forEach { stage ->
                assertNotNull("${folder.name} stage ${stage.key} lottie", skin.stage(stage).lottieAsset)
            }
            assertNotNull("${folder.name} sound", skin.soundAsset)
        }
    }

    @Test
    fun `every mascot is fully localised in hebrew and english`() {
        folders().forEach { folder ->
            val skin = parse(folder)
            listOf("he", "en").forEach { lang ->
                assertNotNull(skin.name.byLanguage[lang])
                MascotStage.entries.forEach { stage ->
                    val texts =
                        skin
                            .stage(stage)
                            .texts.byLanguage[lang]
                            .orEmpty()
                    assertTrue("${folder.name}/$lang/${stage.key}", texts.size >= MascotSkin.MIN_TEXTS_PER_STAGE)
                }
                assertEquals(
                    MascotStage.entries.size,
                    skin.summaries.byLanguage
                        .getValue(lang)
                        .size,
                )
            }
        }
    }

    @Test
    fun `block message pool is at least forty messages per language`() {
        listOf("he", "en").forEach { lang ->
            val total =
                folders().sumOf { folder ->
                    parse(folder)
                        .blockMessages.byLanguage
                        .getValue(lang)
                        .values
                        .sumOf { it.size }
                }
            assertTrue("$lang has $total block messages", total >= MIN_BLOCK_MESSAGES)
        }
    }

    @Test
    fun `personalities and reactions are all distinct`() {
        val skins = folders().map { parse(it) }
        assertEquals(skins.size, skins.map { it.personality }.toSet().size)
        assertEquals(skins.size, skins.map { it.reaction }.toSet().size)
        assertEquals(skins.size, skins.map { it.themeColorArgb }.toSet().size)
    }

    private companion object {
        const val MIN_BLOCK_MESSAGES = 40
    }
}
