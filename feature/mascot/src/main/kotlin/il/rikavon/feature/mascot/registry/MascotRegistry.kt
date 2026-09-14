package il.rikavon.feature.mascot.registry

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.di.IoDispatcher
import il.rikavon.feature.mascot.model.MascotSkin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers mascots at startup by scanning `assets/mascots/<id>/manifest.json`.
 * Dropping a new folder into assets is all it takes; invalid folders are logged and skipped.
 */
@Singleton
class MascotRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val parser = MascotManifestParser()
    private val mutex = Mutex()
    private val _skins = MutableStateFlow<List<MascotSkin>>(emptyList())
    private val _errors = MutableStateFlow<List<String>>(emptyList())
    private var loaded = false

    /** All valid mascots, in folder order. Empty until [load] completes. */
    val skins: StateFlow<List<MascotSkin>> = _skins.asStateFlow()

    /** Human-readable validation failures for developers adding mascots. */
    val errors: StateFlow<List<String>> = _errors.asStateFlow()

    suspend fun load(force: Boolean = false): List<MascotSkin> =
        mutex.withLock {
            if (loaded && !force) return@withLock _skins.value
            val result = withContext(io) { scan() }
            _skins.value = result.first
            _errors.value = result.second
            loaded = true
            result.first
        }

    suspend fun byId(id: String): MascotSkin? = load().firstOrNull { it.id == id }

    /** The requested skin, or the first available one if it does not exist. */
    suspend fun byIdOrDefault(id: String): MascotSkin? = byId(id) ?: load().firstOrNull()

    private fun scan(): Pair<List<MascotSkin>, List<String>> {
        val assets = context.assets
        val folders = runCatching { assets.list(MascotManifestParser.ASSET_ROOT)?.toList() }.getOrNull().orEmpty()
        val skins = mutableListOf<MascotSkin>()
        val errors = mutableListOf<String>()
        for (folder in folders.sorted()) {
            val files =
                runCatching {
                    assets.list("${MascotManifestParser.ASSET_ROOT}/$folder")?.toSet()
                }.getOrNull().orEmpty()
            if (MascotManifestParser.MANIFEST_FILE !in files) continue
            val manifestPath = "${MascotManifestParser.ASSET_ROOT}/$folder/${MascotManifestParser.MANIFEST_FILE}"
            val text =
                runCatching { assets.open(manifestPath).bufferedReader().use { it.readText() } }
                    .getOrElse {
                        errors += "$folder: cannot read manifest (${it.message})"
                        continue
                    }
            runCatching { parser.parse(folder, text) { name -> name in files } }
                .onSuccess { skins += it }
                .onFailure {
                    errors += it.message ?: "$folder: invalid manifest"
                    Log.w(TAG, "Skipping mascot '$folder': ${it.message}")
                }
        }
        return skins to errors
    }

    companion object {
        private const val TAG = "MascotRegistry"
    }
}
