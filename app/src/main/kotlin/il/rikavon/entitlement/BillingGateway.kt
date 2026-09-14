package il.rikavon.entitlement

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import il.rikavon.core.data.repo.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single integration point for a store billing library. The rest of the app only reads
 * [SettingsRepository]'s premium flag, so wiring Play Billing means implementing this interface,
 * verifying the purchase, and calling [SettingsRepository.setPremium].
 */
interface BillingGateway {
    fun isAvailable(): Boolean

    suspend fun purchasePremium()

    suspend fun restore()
}

/** Shipped build: no store SDK is bundled, so purchases are unavailable and the free tier applies. */
@Singleton
class NoBillingGateway @Inject constructor(private val settings: SettingsRepository) : BillingGateway {
    override fun isAvailable(): Boolean = false

    override suspend fun purchasePremium() = Unit

    override suspend fun restore() {
        settings.setPremium(settings.current().premium)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EntitlementModule {
    @Binds
    abstract fun bindBillingGateway(impl: NoBillingGateway): BillingGateway
}
