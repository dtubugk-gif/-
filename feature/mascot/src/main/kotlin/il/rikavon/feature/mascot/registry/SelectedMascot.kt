package il.rikavon.feature.mascot.registry

import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.feature.mascot.model.MascotSkin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/** The skin the user picked, falling back to the first valid mascot if the folder disappeared. */
@Singleton
class SelectedMascot @Inject constructor(
    private val registry: MascotRegistry,
    private val settings: SettingsRepository,
) {
    val skin: Flow<MascotSkin?> =
        combine(
            registry.skins.onStart { registry.load() },
            settings.settings.map { it.selectedMascotId }.distinctUntilChanged(),
        ) { skins, id -> skins.firstOrNull { it.id == id } ?: skins.firstOrNull() }

    suspend fun current(): MascotSkin? = registry.byIdOrDefault(settings.current().selectedMascotId)
}
