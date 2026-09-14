package il.rikavon.feature.mascot.ui.gallery

import il.rikavon.feature.mascot.model.MascotStage
import kotlinx.coroutines.flow.Flow

/**
 * Supplies the mascot stage that reflects the user's day right now. Implemented by the blocker feature
 * (which owns the focus score) and bound through Hilt, so the mascot module stays independent of it.
 */
interface CurrentStageSource {
    val stage: Flow<MascotStage>
}
