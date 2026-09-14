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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import il.rikavon.R
import il.rikavon.core.ui.components.PrimaryButton
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SecondaryButton
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Spacing
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

/** Battery guide: one card per step, the system exemption is the filled action, the vendor screen is secondary. */
@Composable
fun BatteryGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vendor = remember { Vendor.detect() }
    val scrollBehavior = rememberPinnedTopBarBehavior()
    Scaffold(
        topBar = {
            RikavonTopBar(
                title = stringResource(R.string.battery_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.battery_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalExtraColors.current.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
            )
            SectionLabel(stringResource(R.string.battery_step_system))
            StepCard(
                body = stringResource(R.string.battery_step_system_body),
                action = {
                    PrimaryButton(
                        text = stringResource(R.string.battery_open_system),
                        onClick = { context.openIgnoreBatteryOptimizations() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
            SectionLabel(stringResource(R.string.battery_section_vendor), modifier = Modifier.padding(top = Spacing.md))
            StepCard(
                title = stringResource(vendor.titleRes),
                body = stringResource(vendor.bodyRes),
                action = {
                    SecondaryButton(
                        text =
                            stringResource(
                                if (vendor ==
                                    Vendor.OTHER
                                ) {
                                    R.string.battery_open_app_info
                                } else {
                                    R.string.battery_open_vendor
                                },
                            ),
                        onClick = { context.openVendorSettings(vendor) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
    }
}

@Composable
private fun StepCard(body: String, action: @Composable () -> Unit, title: String? = null) {
    SurfaceCard(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
        Column {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.lg))
            action()
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
