package il.rikavon.feature.mascot.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import il.rikavon.feature.mascot.registry.MascotTexts
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MascotModule {
    @Provides
    @Singleton
    fun provideMascotTexts(): MascotTexts = MascotTexts()
}
