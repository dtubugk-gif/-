package il.rikavon.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import il.rikavon.core.data.db.RikavonDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    private const val SETTINGS_STORE = "rikavon_settings"

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): RikavonDatabase =
        Room
            .databaseBuilder(context, RikavonDatabase::class.java, RikavonDatabase.NAME)
            .addMigrations(RikavonDatabase.MIGRATION_1_2, RikavonDatabase.MIGRATION_2_3, RikavonDatabase.MIGRATION_3_4)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideDailyUsageDao(db: RikavonDatabase) = db.dailyUsageDao()

    @Provides
    fun provideAppLimitDao(db: RikavonDatabase) = db.appLimitDao()

    @Provides
    fun provideScheduleDao(db: RikavonDatabase) = db.scheduleDao()

    @Provides
    fun provideDailySummaryDao(db: RikavonDatabase) = db.dailySummaryDao()

    @Provides
    fun provideAchievementDao(db: RikavonDatabase) = db.achievementDao()

    @Provides
    fun provideBlockEventDao(db: RikavonDatabase) = db.blockEventDao()

    @Provides
    @Singleton
    fun providePreferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SETTINGS_STORE) }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}
