package com.dtubugk.shakelight.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.GridLayoutManager
import com.dtubugk.shakelight.R
import com.dtubugk.shakelight.core.Prefs
import com.dtubugk.shakelight.core.TorchController
import com.dtubugk.shakelight.databinding.ActivityMainBinding
import com.dtubugk.shakelight.shake.ShakeService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var renderingSwitch = false
    private val torchListener: (Boolean) -> Unit = { render() }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startShake() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.featureGrid.layoutManager = GridLayoutManager(this, 2)
        binding.featureGrid.adapter = FeatureAdapter(Features.all) { startActivity(Intent(this, it.screen)) }

        binding.shakeSwitch.setOnCheckedChangeListener { _, checked ->
            if (renderingSwitch) return@setOnCheckedChangeListener
            if (checked) enableShake() else disableShake()
        }
        binding.batteryButton.setOnClickListener { requestUnrestrictedBattery() }
    }

    override fun onStart() {
        super.onStart()
        TorchController.addListener(torchListener)
    }

    override fun onStop() {
        TorchController.removeListener(torchListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // The system may have killed the service; bring it back while the app is visible.
        if (shakeEnabled() && !ShakeService.running) ShakeService.start(this)
        render()
    }

    private fun shakeEnabled() = Prefs.get(this).getBoolean(Prefs.SHAKE_ENABLED, false)

    private fun enableShake() {
        Prefs.get(this).edit { putBoolean(Prefs.SHAKE_ENABLED, true) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startShake()
        }
    }

    private fun startShake() {
        ShakeService.start(this)
        render()
    }

    private fun disableShake() {
        Prefs.get(this).edit { putBoolean(Prefs.SHAKE_ENABLED, false) }
        ShakeService.stop(this)
        render()
    }

    private fun render() {
        val enabled = shakeEnabled()
        renderingSwitch = true
        binding.shakeSwitch.isChecked = enabled
        renderingSwitch = false
        binding.shakeStatus.setText(if (enabled) R.string.shake_status_on else R.string.shake_status_off)
        binding.noFlashWarning.visibility = if (TorchController.hasFlash(this)) View.GONE else View.VISIBLE

        val power = getSystemService(PowerManager::class.java)
        val restricted = !power.isIgnoringBatteryOptimizations(packageName)
        binding.batteryRow.visibility = if (enabled && restricted) View.VISIBLE else View.GONE
    }

    @SuppressLint("BatteryLife")
    private fun requestUnrestrictedBattery() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        try {
            startActivity(direct)
        } catch (e: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }
}
