package il.rikavon.feature.blocker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import il.rikavon.feature.mascot.ui.gallery.CurrentStageSource

@Module
@InstallIn(SingletonComponent::class)
abstract class BlockerModule {
    @Binds
    abstract fun bindCurrentStageSource(provider: FocusScoreProvider): CurrentStageSource
}
