package il.rikavon.core.data.usage

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.di.IoDispatcher
import il.rikavon.core.data.model.InstalledApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lists launchable apps. Requires QUERY_ALL_PACKAGES (declared and justified for Play). */
@Singleton
class InstalledAppsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val packageManager: PackageManager get() = context.packageManager

    suspend fun launchableApps(): List<InstalledApp> =
        withContext(io) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            resolved
                .asSequence()
                .map { it.activityInfo.applicationInfo }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .map { info ->
                    InstalledApp(
                        packageName = info.packageName,
                        label = info.loadLabel(packageManager).toString(),
                        isSystem =
                            info.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                                info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0,
                    )
                }.sortedBy { it.label.lowercase() }
                .toList()
        }

    private val labels = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun label(packageName: String): String =
        labels.getOrPut(packageName) {
            runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            }.getOrDefault(packageName)
        }

    fun icon(packageName: String): Drawable? =
        runCatching {
            packageManager.getApplicationIcon(packageName)
        }.getOrNull()
}
