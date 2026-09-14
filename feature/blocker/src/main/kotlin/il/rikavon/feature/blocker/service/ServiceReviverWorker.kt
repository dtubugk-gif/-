package il.rikavon.feature.blocker.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import il.rikavon.core.data.repo.DayRolloverUseCase
import java.util.concurrent.TimeUnit

/**
 * Periodic safety net: if the system killed the service (aggressive OEM battery managers, low memory),
 * bring it back and make sure the day rollover happened.
 */
@HiltWorker
class ServiceReviverWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val starter: ServiceStarter,
    private val rollover: DayRolloverUseCase,
    private val midnight: MidnightAlarmScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        rollover.runIfDue()
        midnight.schedule()
        starter.startIfConfigured()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "rikavon_service_reviver"
        private const val INTERVAL_MINUTES = 15L

        fun enqueue(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<ServiceReviverWorker>(
                    INTERVAL_MINUTES,
                    TimeUnit.MINUTES,
                ).build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
