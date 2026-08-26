package com.callsoundboard.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.callsoundboard.app.audio.SoundPlayer
import com.callsoundboard.app.audio.SpeakerRouter
import com.callsoundboard.app.data.AppSettings
import com.callsoundboard.app.data.ClipImporter
import com.callsoundboard.app.data.SoundRepository
import com.callsoundboard.app.databinding.ActivityMainBinding
import com.callsoundboard.app.model.SoundClip
import com.callsoundboard.app.overlay.OverlayService
import com.callsoundboard.app.ui.SoundAdapter
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: SoundRepository
    private lateinit var adapter: SoundAdapter

    private val pickAudio =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onAudioPicked(uri)
        }

    private val pickDownloadsTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) onDownloadsTreePicked(uri)
        }

    private val requestPhone =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updatePermissionUi()
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SoundRepository(this)

        adapter = SoundAdapter(
            onPlay = { clip ->
                SpeakerRouter.enableSpeaker(this)
                SoundPlayer.play(this, Uri.parse(clip.uri))
            },
            onDelete = { clip ->
                repository.remove(clip.id)
                refreshList()
            }
        )
        binding.rvClips.layoutManager = LinearLayoutManager(this)
        binding.rvClips.adapter = adapter

        binding.btnAddClip.setOnClickListener {
            pickAudio.launch(arrayOf("audio/*"))
        }

        binding.btnLoadDownloads.setOnClickListener {
            val saved = AppSettings.getDownloadsTreeUri(this)
            if (saved != null) {
                scanDownloadsTree(saved, silent = false)
            } else {
                Toast.makeText(this, R.string.hint_pick_downloads, Toast.LENGTH_LONG).show()
                pickDownloadsTree.launch(null)
            }
        }

        binding.btnChangeDownloads.setOnClickListener {
            Toast.makeText(this, R.string.hint_pick_downloads, Toast.LENGTH_LONG).show()
            pickDownloadsTree.launch(null)
        }

        binding.btnShowBubble.setOnClickListener { showBubble() }

        binding.btnStop.setOnClickListener { SoundPlayer.stop() }

        binding.switchAutoBubble.isChecked = AppSettings.isAutoBubbleEnabled(this)
        binding.switchAutoBubble.setOnCheckedChangeListener { _, checked ->
            AppSettings.setAutoBubbleEnabled(this, checked)
        }

        binding.btnGrantOverlay.setOnClickListener { openOverlaySettings() }
        binding.btnGrantPhone.setOnClickListener {
            requestPhone.launch(Manifest.permission.READ_PHONE_STATE)
        }

        maybeRequestNotifications()

        // A file may have been shared into the app from the Share sheet.
        handleIncomingShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShare(intent)
    }

    override fun onResume() {
        super.onResume()
        // Auto-refresh from the granted Downloads folder so newly downloaded
        // files show up without re-picking.
        AppSettings.getDownloadsTreeUri(this)?.let { scanDownloadsTree(it, silent = true) }
        refreshList()
        updatePermissionUi()
    }

    // ---- Clip management ----------------------------------------------------

    private fun onAudioPicked(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Some providers don't support persistable grants; the URI may still
            // work for this session, but warn the user it might not persist.
        }
        val label = queryDisplayName(uri)
        repository.add(SoundClip(UUID.randomUUID().toString(), label, uri.toString()))
        refreshList()
        Toast.makeText(this, R.string.toast_clip_added, Toast.LENGTH_SHORT).show()
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = getString(R.string.default_clip_label)
        try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx)?.let { name = it }
                }
            }
        } catch (_: Exception) {
        }
        return name
    }

    private fun refreshList() {
        val clips = repository.getAll()
        adapter.submit(clips)
        binding.tvEmpty.visibility = if (clips.isEmpty()) android.view.View.VISIBLE
        else android.view.View.GONE
    }

    // ---- Bulk import from the Downloads folder ------------------------------

    private fun onDownloadsTreePicked(treeUri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        AppSettings.setDownloadsTreeUri(this, treeUri.toString())
        scanDownloadsTree(treeUri.toString(), silent = false)
    }

    /**
     * Enumerates the granted folder and adds every audio file not already saved.
     * The persisted tree permission covers the child file URIs, so playback works
     * later without any per-file grant.
     */
    private fun scanDownloadsTree(treeUriString: String, silent: Boolean) {
        val treeUri = Uri.parse(treeUriString)
        val found = mutableListOf<SoundClip>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
            )
            contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (c.moveToNext()) {
                    val docId = c.getString(idIdx) ?: continue
                    val name = c.getString(nameIdx) ?: continue
                    val mime = if (!c.isNull(mimeIdx)) c.getString(mimeIdx) else null
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    if (!isAudio(mime, name)) continue
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    found.add(SoundClip(UUID.randomUUID().toString(), name, fileUri.toString()))
                }
            }
        } catch (_: Exception) {
            if (!silent) {
                Toast.makeText(this, R.string.toast_downloads_scan_failed, Toast.LENGTH_LONG).show()
            }
            return
        }

        val added = repository.addAllNew(found)
        refreshList()
        if (!silent) {
            val msg = if (added > 0) getString(R.string.toast_downloads_added, added)
            else getString(R.string.toast_downloads_none)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAudio(mime: String?, name: String): Boolean {
        if (mime != null && mime.startsWith("audio/")) return true
        val lower = name.lowercase()
        return AUDIO_EXTENSIONS.any { lower.endsWith(it) }
    }

    // ---- Import from the system Share sheet ---------------------------------

    private fun handleIncomingShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> extractStream(intent)?.let { listOf(it) } ?: emptyList()
            Intent.ACTION_SEND_MULTIPLE -> extractStreams(intent)
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        // Clear the action so a later recreate/resume doesn't re-import.
        intent.action = null

        val imported = uris.mapNotNull { ClipImporter.import(this, it) }
        val added = repository.addAllNew(imported)
        refreshList()
        val msg = if (added > 0) getString(R.string.toast_shared_added, added)
        else getString(R.string.toast_pick_failed)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun extractStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun extractStreams(intent: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }) ?: emptyList()

    // ---- Bubble / permissions ----------------------------------------------

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_need_overlay, Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }
        val svc = Intent(this, OverlayService::class.java)
            .putExtra(OverlayService.EXTRA_FROM_CALL, false)
        ContextCompat.startForegroundService(this, svc)
    }

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun updatePermissionUi() {
        val overlayOk = Settings.canDrawOverlays(this)
        binding.btnGrantOverlay.text = getString(R.string.grant_overlay) + "  " +
            getString(if (overlayOk) R.string.permission_granted else R.string.permission_missing)

        val phoneOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        binding.btnGrantPhone.text = getString(R.string.grant_phone) + "  " +
            getString(if (phoneOk) R.string.permission_granted else R.string.permission_missing)
    }

    companion object {
        private val AUDIO_EXTENSIONS = listOf(
            ".mp3", ".m4a", ".aac", ".wav", ".ogg", ".oga", ".opus",
            ".flac", ".3gp", ".amr", ".mid", ".midi", ".wma", ".mka"
        )
    }
}
