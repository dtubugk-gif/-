package com.myvpn.app

import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.text.format.Formatter
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val app get() = application as VpnApp
    private var config: Config? = null
    private var busy = false

    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var statsText: TextView
    private lateinit var toggleButton: MaterialButton
    private lateinit var serverText: TextView
    private lateinit var removeButton: MaterialButton

    /** Android asks the user once to allow this app to create a VPN. */
    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) connect() else toast(R.string.permission_denied)
        }

    private val scanQr = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { importText(it) }
    }

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importUri(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        statusIcon = findViewById(R.id.status_icon)
        statusText = findViewById(R.id.status_text)
        detailText = findViewById(R.id.detail_text)
        statsText = findViewById(R.id.stats_text)
        toggleButton = findViewById(R.id.toggle_button)
        serverText = findViewById(R.id.server_text)
        removeButton = findViewById(R.id.remove_button)

        toggleButton.setOnClickListener { onToggle() }
        findViewById<View>(R.id.scan_button).setOnClickListener { startScan() }
        findViewById<View>(R.id.file_button).setOnClickListener { pickFile.launch(arrayOf("*/*")) }
        findViewById<View>(R.id.paste_button).setOnClickListener { showPasteDialog() }
        removeButton.setOnClickListener { confirmRemove() }

        config = app.configStore.load()

        lifecycleScope.launch {
            // Pick up the real state in case the VPN was already running.
            val running = withContext(Dispatchers.IO) {
                runCatching { app.backend.getState(app.tunnel) }.getOrNull()
            }
            running?.let { app.tunnel.onStateChange(it) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { app.tunnel.state.collect { render() } }
                launch { pollStatistics() }
            }
        }
        render()
    }

    // ---- Connect / disconnect ------------------------------------------------------------

    private fun onToggle() {
        if (busy) return
        if (app.tunnel.state.value == Tunnel.State.UP) {
            disconnect()
        } else {
            val permissionIntent = VpnService.prepare(this)
            if (permissionIntent != null) vpnPermission.launch(permissionIntent) else connect()
        }
    }

    private fun connect() = setTunnel(Tunnel.State.UP)

    private fun disconnect() = setTunnel(Tunnel.State.DOWN)

    private fun setTunnel(target: Tunnel.State, after: (() -> Unit)? = null) {
        val cfg = config ?: return
        busy = true
        render()
        lifecycleScope.launch {
            try {
                val newState = withContext(Dispatchers.IO) {
                    app.backend.setState(app.tunnel, target, cfg)
                }
                app.tunnel.onStateChange(newState)
                after?.invoke()
            } catch (e: Exception) {
                showError(getString(R.string.error_connect, e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
                render()
            }
        }
    }

    private suspend fun pollStatistics() {
        while (true) {
            if (app.tunnel.state.value == Tunnel.State.UP) {
                val stats = withContext(Dispatchers.IO) {
                    runCatching { app.backend.getStatistics(app.tunnel) }.getOrNull()
                }
                if (stats != null) {
                    val handshake = stats.peers()
                        .mapNotNull { stats.peer(it)?.latestHandshakeEpochMillis() }
                        .maxOrNull() ?: 0L
                    val handshakeText = if (handshake > 0) {
                        val secs = ((System.currentTimeMillis() - handshake) / 1000).coerceAtLeast(0)
                        getString(R.string.handshake_ago, secs)
                    } else {
                        getString(R.string.handshake_waiting)
                    }
                    statsText.text = getString(
                        R.string.stats,
                        Formatter.formatShortFileSize(this, stats.totalRx()),
                        Formatter.formatShortFileSize(this, stats.totalTx()),
                        handshakeText,
                    )
                }
            }
            delay(1000)
        }
    }

    // ---- Importing configs ---------------------------------------------------------------

    private fun startScan() {
        scanQr.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.scan_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    private fun importUri(uri: Uri) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        // Configs are tiny; refuse anything big to avoid reading random files.
                        val bytes = stream.readNBytesCompat(64 * 1024)
                        String(bytes)
                    }
                }.getOrNull()
            }
            if (text == null) showError(getString(R.string.error_read_file)) else importText(text)
        }
    }

    private fun importText(text: String) {
        val parsed = try {
            ConfigStore.parse(text)
        } catch (e: Exception) {
            showError(getString(R.string.error_bad_config))
            return
        }
        app.configStore.save(parsed)
        config = parsed
        toast(R.string.config_saved)
        // If already connected, switch to the new server right away.
        if (app.tunnel.state.value == Tunnel.State.UP) connect() else render()
    }

    private fun showPasteDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 8
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            hint = getString(R.string.paste_hint)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.paste_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ -> importText(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRemove() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_title)
            .setMessage(R.string.remove_message)
            .setPositiveButton(R.string.remove) { _, _ ->
                val removeNow = {
                    app.configStore.delete()
                    config = null
                    render()
                }
                if (app.tunnel.state.value == Tunnel.State.UP) {
                    setTunnel(Tunnel.State.DOWN, removeNow)
                } else {
                    removeNow()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- UI ------------------------------------------------------------------------------

    private fun render() {
        val cfg = config
        val up = app.tunnel.state.value == Tunnel.State.UP

        val (statusRes, colorRes) = when {
            busy && up -> R.string.status_disconnecting to R.color.status_busy
            busy -> R.string.status_connecting to R.color.status_busy
            up -> R.string.status_connected to R.color.status_connected
            cfg == null -> R.string.status_no_config to R.color.status_off
            else -> R.string.status_disconnected to R.color.status_off
        }
        val color = ContextCompat.getColor(this, colorRes)
        statusText.setText(statusRes)
        statusText.setTextColor(color)
        statusIcon.imageTintList = ColorStateList.valueOf(color)

        toggleButton.isEnabled = cfg != null && !busy
        toggleButton.setText(if (up) R.string.disconnect else R.string.connect)
        toggleButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (up) R.color.button_disconnect else R.color.button_connect)
        )

        statsText.visibility = if (up) View.VISIBLE else View.GONE
        if (!up) statsText.text = ""

        if (cfg == null) {
            detailText.setText(R.string.detail_no_config)
            serverText.setText(R.string.server_none)
            removeButton.visibility = View.GONE
        } else {
            val endpoint = cfg.peers.firstOrNull()?.endpoint?.orElse(null)?.toString()
                ?: getString(R.string.unknown)
            val address = cfg.`interface`.addresses.joinToString { it.toString() }
            detailText.text = getString(R.string.detail_server, endpoint)
            serverText.text = getString(R.string.server_info, endpoint, address)
            removeButton.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
}

/** InputStream.readNBytes needs API 33; this works on all supported versions. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
