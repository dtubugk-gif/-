package il.rikavon.ui.battery

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.rikavon.R
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionHeader
import il.rikavon.core.ui.components.TouchTarget
import java.util.Locale

/** Vendors that ship their own battery managers which kill foreground services. */
enum class Vendor(val titleRes: Int, val bodyRes: Int, val components: List<ComponentName>) {
    XIAOMI(
        R.string.battery_vendor_xiaomi,
        R.string.battery_vendor_xiaomi_body,
        listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
        ),
    ),
    SAMSUNG(
        R.string.battery_vendor_samsung,
        R.string.battery_vendor_samsung_body,
        listOf(
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity",
            ),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ),
    ),
    HUAWEI(
        R.string.battery_vendor_huawei,
        R.string.battery_vendor_huawei_body,
        listOf(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ),
    ),
    OPPO(
        R.string.battery_vendor_oppo,
        R.string.battery_vendor_oppo_body,
        listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ),
    ),
    VIVO(
        R.string.battery_vendor_vivo,
        R.string.battery_vendor_vivo_body,
        listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ),
    ),
    ONEPLUS(
        R.string.battery_vendor_oneplus,
        R.string.battery_vendor_oneplus_body,
        listOf(
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ),
    ),
    OTHER(R.string.battery_vendor_other, R.string.battery_vendor_other_body, emptyList()),
    ;

    companion object {
        fun detect(manufacturer: String = Build.MANUFACTURER, brand: String = Build.BRAND): Vendor {
            val m = manufacturer.lowercase(Locale.ROOT)
            val b = brand.lowercase(Locale.ROOT)
            return when {
                "xiaomi" in m || "redmi" in b || "poco" in b -> XIAOMI
                "samsung" in m -> SAMSUNG
                "huawei" in m || "honor" in m -> HUAWEI
                "oppo" in m || "realme" in m || "realme" in b -> OPPO
                "vivo" in m -> VIVO
                "oneplus" in m -> ONEPLUS
                else -> OTHER
            }
        }
    }
}

@Composable
fun BatteryGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vendor = remember { Vendor.detect() }
    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.battery_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = ScreenPadding),
        ) {
            Text(
                text = stringResource(R.string.battery_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
            )
            SectionHeader(stringResource(R.string.battery_step_system))
            Text(
                text = stringResource(R.string.battery_step_system_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { context.openIgnoreBatteryOptimizations() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding)
                        .heightIn(min = TouchTarget + 8.dp),
            ) {
                Text(stringResource(R.string.battery_open_system))
            }
            SectionHeader(stringResource(vendor.titleRes))
            Text(
                text = stringResource(vendor.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { context.openVendorSettings(vendor) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding)
                        .heightIn(min = TouchTarget + 8.dp),
            ) {
                Text(
                    stringResource(
                        if (vendor ==
                            Vendor.OTHER
                        ) {
                            R.string.battery_open_app_info
                        } else {
                            R.string.battery_open_vendor
                        },
                    ),
                )
            }
        }
    }
}

private fun Context.openIgnoreBatteryOptimizations() {
    val intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }.onFailure {
        startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun Context.openVendorSettings(vendor: Vendor) {
    for (component in vendor.components) {
        val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
