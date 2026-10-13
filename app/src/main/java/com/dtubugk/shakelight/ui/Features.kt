package com.dtubugk.shakelight.ui

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dtubugk.shakelight.R
import com.dtubugk.shakelight.cast.CastToPcActivity
import com.dtubugk.shakelight.projector.ProjectorActivity

data class Feature(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @DrawableRes val icon: Int,
    val screen: Class<out Activity>,
)

/**
 * Every tool on the home screen. To add a new feature: build its Activity, register it in
 * AndroidManifest.xml, and add one line here — the home grid picks it up automatically.
 */
object Features {
    val all = listOf(
        Feature(R.string.feature_flashlight, R.string.feature_flashlight_desc, R.drawable.ic_flashlight, FlashlightActivity::class.java),
        Feature(R.string.feature_cast_pc, R.string.feature_cast_pc_desc, R.drawable.ic_computer, CastToPcActivity::class.java),
        Feature(R.string.feature_screen_light, R.string.feature_screen_light_desc, R.drawable.ic_lightbulb, ScreenLightActivity::class.java),
        Feature(R.string.feature_projector, R.string.feature_projector_desc, R.drawable.ic_projector, ProjectorActivity::class.java),
        Feature(R.string.feature_settings, R.string.feature_settings_desc, R.drawable.ic_settings, SettingsActivity::class.java),
    )
}
