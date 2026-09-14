package com.duplicatecleaner.app.ui

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duplicatecleaner.app.Permissions
import com.duplicatecleaner.app.delete.Deleter
import com.duplicatecleaner.app.model.DuplicateGroup
import com.duplicatecleaner.app.model.FileEntry
import com.duplicatecleaner.app.model.MediaKind
import com.duplicatecleaner.app.model.ScanPhase
import com.duplicatecleaner.app.model.ScanProgress
import com.duplicatecleaner.app.scanner.DuplicateFinder
import com.duplicatecleaner.app.scanner.FileSystemSource
import com.duplicatecleaner.app.scanner.HashCache
import com.duplicatecleaner.app.scanner.MediaStoreSource
import com.duplicatecleaner.app.scanner.StorageRoots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

enum class Stage { START, SCANNING, RESULTS }

data class SystemConfirmation(val intentSender: IntentSender)

data class DeletionState(val done: Int, val total: Int, val waitingForSystem: Boolean = false)

data class PreviewState(val groupId: String, val fileKey: String)

sealed interface DialogState {
    data class ConfirmDeleteMany(val entries: List<FileEntry>) : DialogState
    data class ConfirmDeleteOne(val entry: FileEntry) : DialogState
}

sealed interface UiMessage {
    data class Deleted(val count: Int, val bytes: Long, val failed: Int) : UiMessage
    data object DeleteCancelled : UiMessage
    data object DeleteFailed : UiMessage
    data class ScanFailed(val reason: String) : UiMessage
    data object OpenFailed : UiMessage
}

data class UiState(
    val stage: Stage = Stage.START,
    val scope: Set<MediaKind> = setOf(MediaKind.IMAGE, MediaKind.VIDEO),
    val hasMediaPermission: Boolean = false,
    val hasPartialMediaAccess: Boolean = false,
    val hasAllFilesAccess: Boolean = false,
    val progress: ScanProgress? = null,
    val groups: List<DuplicateGroup> = emptyList(),
    val hasResults: Boolean = false,
    val scannedFiles: Int = 0,
    /** groupId -> key of the file to keep. Groups absent here keep their first file. */
    val keepers: Map<String, String> = emptyMap(),
    /** Keys of files marked for batch deletion. Never contains a keeper. */
    val selected: Set<String> = emptySet(),
    /** Group ids the user collapsed; everything else is shown expanded. */
    val collapsed: Set<String> = emptySet(),
    val filter: MediaKind? = null,
    val preview: PreviewState? = null,
    val dialog: DialogState? = null,
    val deletion: DeletionState? = null,
    val systemConfirmation: SystemConfirmation? = null,
    val message: UiMessage? = null,
) {
    val canScanMedia: Boolean get() = hasMediaPermission || hasAllFilesAccess

    val canStartScan: Boolean
        get() = stage != Stage.SCANNING && scope.isNotEmpty() && canScanMedia &&
            (MediaKind.OTHER !in scope || hasAllFilesAccess)

    val visibleGroups: List<DuplicateGroup>
        get() = filter?.let { f -> groups.filter { it.kind == f } } ?: groups

    fun keeperOf(group: DuplicateGroup): String = keepers[group.id] ?: group.files.first().key

    val totalWastedBytes: Long get() = groups.sumOf { it.wastedBytes }
    val totalRedundantFiles: Int get() = groups.sumOf { it.count - 1 }

    val visibleSelectedEntries: List<FileEntry>
        get() = visibleGroups.flatMap { g -> g.files.filter { it.key in selected } }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var deleteJob: Job? = null
    private var pendingSystemResult: CompletableDeferred<Boolean>? = null
    private val hashCache = HashCache(File(application.filesDir, "hash-cache.bin"))
    private val deleter = Deleter(application)

    init {
        refreshPermissions()
    }

    // ---------------------------------------------------------------- permissions & scope

    fun refreshPermissions() {
        _state.update {
            it.copy(
                hasMediaPermission = Permissions.hasMediaPermission(app),
                hasPartialMediaAccess = Permissions.hasPartialMediaAccess(app),
                hasAllFilesAccess = Permissions.hasAllFilesAccess(app),
            )
        }
    }

    fun toggleKind(kind: MediaKind) {
        _state.update { s ->
            val scope = s.scope.toMutableSet()
            if (!scope.add(kind)) scope.remove(kind)
            s.copy(scope = scope)
        }
    }

    // ---------------------------------------------------------------- scanning

    fun startScan() {
        val snapshot = _state.value
        if (!snapshot.canStartScan) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = Stage.SCANNING,
                    progress = ScanProgress(ScanPhase.LISTING),
                    preview = null,
                    dialog = null,
                )
            }
            try {
                val kinds = snapshot.scope
                val entries = withContext(Dispatchers.IO) {
                    val onCount: (Int) -> Unit = { n ->
                        _state.update { it.copy(progress = ScanProgress(ScanPhase.LISTING, done = n)) }
                    }
                    if (snapshot.hasAllFilesAccess) {
                        FileSystemSource.walk(StorageRoots.get(app), kinds, onCount) { isActive }
                    } else {
                        MediaStoreSource.query(app, kinds, onCount) { isActive }
                    }
                }
                withContext(Dispatchers.IO) { hashCache.load() }
                val finder = DuplicateFinder(open = ::openStream, cache = hashCache)
                val groups = withContext(Dispatchers.Default) {
                    finder.find(entries) { progress -> _state.update { it.copy(progress = progress) } }
                }
                withContext(Dispatchers.IO) { hashCache.save() }
                _state.update {
                    it.copy(
                        stage = Stage.RESULTS,
                        progress = null,
                        groups = groups,
                        hasResults = true,
                        scannedFiles = entries.size,
                        keepers = emptyMap(),
                        selected = defaultSelection(groups),
                        collapsed = emptySet(),
                        filter = null,
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(stage = Stage.START, progress = null) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        stage = Stage.START,
                        progress = null,
                        message = UiMessage.ScanFailed(e.message ?: e.javaClass.simpleName),
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    fun showResults() {
        if (_state.value.hasResults) _state.update { it.copy(stage = Stage.RESULTS) }
    }

    fun backToStart() {
        _state.update { it.copy(stage = Stage.START, preview = null) }
    }

    private fun openStream(entry: FileEntry): InputStream {
        entry.uri?.let { uri ->
            return app.contentResolver.openInputStream(Uri.parse(uri))
                ?: throw IOException("Cannot open $uri")
        }
        return FileInputStream(entry.path)
    }

    private fun defaultSelection(groups: List<DuplicateGroup>): Set<String> =
        groups.flatMapTo(HashSet()) { g -> g.files.drop(1).map { it.key } }

    // ---------------------------------------------------------------- results interaction

    fun setFilter(kind: MediaKind?) {
        _state.update { it.copy(filter = kind) }
    }

    fun toggleCollapsed(groupId: String) {
        _state.update { s ->
            s.copy(collapsed = if (groupId in s.collapsed) s.collapsed - groupId else s.collapsed + groupId)
        }
    }

    fun setKeeper(groupId: String, fileKey: String) {
        _state.update { s ->
            val group = s.groups.firstOrNull { it.id == groupId } ?: return@update s
            if (group.files.none { it.key == fileKey }) return@update s
            val previous = s.keeperOf(group)
            if (previous == fileKey) return@update s
            s.copy(
                keepers = s.keepers + (groupId to fileKey),
                selected = s.selected - fileKey + previous,
            )
        }
    }

    fun toggleSelected(fileKey: String) {
        _state.update { s ->
            val group = s.groups.firstOrNull { g -> g.files.any { it.key == fileKey } } ?: return@update s
            if (s.keeperOf(group) == fileKey) return@update s
            s.copy(selected = if (fileKey in s.selected) s.selected - fileKey else s.selected + fileKey)
        }
    }

    fun selectAllVisible() {
        _state.update { s ->
            val keys = s.visibleGroups.flatMap { g ->
                val keeper = s.keeperOf(g)
                g.files.map { it.key }.filter { it != keeper }
            }
            s.copy(selected = s.selected + keys)
        }
    }

    fun clearVisibleSelection() {
        _state.update { s ->
            val keys = s.visibleGroups.flatMapTo(HashSet()) { g -> g.files.map { it.key } }
            s.copy(selected = s.selected - keys)
        }
    }

    fun openPreview(groupId: String, fileKey: String) {
        _state.update { it.copy(preview = PreviewState(groupId, fileKey)) }
    }

    fun closePreview() {
        _state.update { it.copy(preview = null) }
    }

    // ---------------------------------------------------------------- deletion

    fun requestDeleteSelected() {
        val entries = _state.value.visibleSelectedEntries
        if (entries.isEmpty()) return
        _state.update { it.copy(dialog = DialogState.ConfirmDeleteMany(entries)) }
    }

    fun requestDeleteGroupDuplicates(groupId: String) {
        val s = _state.value
        val group = s.groups.firstOrNull { it.id == groupId } ?: return
        val keeper = s.keeperOf(group)
        val entries = group.files.filter { it.key != keeper }
        if (entries.isEmpty()) return
        _state.update { it.copy(dialog = DialogState.ConfirmDeleteMany(entries)) }
    }

    fun requestDeleteOne(entry: FileEntry) {
        _state.update { it.copy(dialog = DialogState.ConfirmDeleteOne(entry)) }
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun confirmDialog() {
        val dialog = _state.value.dialog ?: return
        _state.update { it.copy(dialog = null) }
        when (dialog) {
            is DialogState.ConfirmDeleteMany -> deleteEntries(dialog.entries)
            is DialogState.ConfirmDeleteOne -> deleteEntries(listOf(dialog.entry))
        }
    }

    private fun deleteEntries(entries: List<FileEntry>) {
        if (entries.isEmpty() || deleteJob?.isActive == true) return
        deleteJob = viewModelScope.launch {
            val total = entries.size
            var deletedCount = 0
            var freedBytes = 0L
            var failedCount = 0
            var cancelled = false
            _state.update { it.copy(deletion = DeletionState(0, total)) }

            val queue = ArrayDeque(entries.chunked(DELETE_BATCH))
            var carry: List<FileEntry> = emptyList()

            fun progress(extra: Int = 0) {
                val done = (deletedCount + failedCount + extra).coerceAtMost(total)
                _state.update { it.copy(deletion = it.deletion?.copy(done = done)) }
            }

            while (true) {
                val batch = when {
                    carry.isNotEmpty() -> carry.also { carry = emptyList() }
                    else -> queue.removeFirstOrNull() ?: break
                }
                val result = withContext(Dispatchers.IO) {
                    deleter.delete(batch) { n -> progress(n) }
                }
                removeFromResults(result.deleted)
                deletedCount += result.deleted.size
                freedBytes += result.deleted.sumOf { it.size }
                failedCount += result.failed.size
                progress()

                val confirmation = result.confirmation
                if (confirmation != null) {
                    val deferred = CompletableDeferred<Boolean>()
                    pendingSystemResult = deferred
                    _state.update {
                        it.copy(
                            systemConfirmation = SystemConfirmation(confirmation.intentSender),
                            deletion = it.deletion?.copy(waitingForSystem = true),
                        )
                    }
                    val ok = deferred.await()
                    pendingSystemResult = null
                    _state.update { it.copy(deletion = it.deletion?.copy(waitingForSystem = false)) }
                    if (!ok) {
                        cancelled = true
                        break
                    }
                    if (confirmation.retryAfterConfirm) {
                        carry = confirmation.entries + result.remaining
                    } else {
                        removeFromResults(confirmation.entries)
                        deletedCount += confirmation.entries.size
                        freedBytes += confirmation.entries.sumOf { it.size }
                        progress()
                        carry = result.remaining
                    }
                } else if (result.remaining.isNotEmpty()) {
                    carry = result.remaining
                }
            }

            val message = when {
                deletedCount == 0 && cancelled -> UiMessage.DeleteCancelled
                deletedCount == 0 -> UiMessage.DeleteFailed
                else -> UiMessage.Deleted(deletedCount, freedBytes, failedCount)
            }
            _state.update { it.copy(deletion = null, message = message) }
        }
    }

    /** Called by the UI right after it launched the system confirmation, so it is not launched twice. */
    fun onSystemConfirmationLaunched() {
        _state.update { it.copy(systemConfirmation = null) }
    }

    fun onSystemConfirmationResult(confirmed: Boolean) {
        pendingSystemResult?.complete(confirmed)
    }

    private fun removeFromResults(deleted: List<FileEntry>) {
        if (deleted.isEmpty()) return
        val gone = deleted.mapTo(HashSet()) { it.key }
        _state.update { s ->
            val groups = ArrayList<DuplicateGroup>(s.groups.size)
            val keepers = HashMap<String, String>()
            for (group in s.groups) {
                val remaining = group.files.filter { it.key !in gone }
                if (remaining.size < 2) continue // a lone survivor is no longer a duplicate
                groups += group.copy(files = remaining)
                val keeper = s.keepers[group.id]?.takeIf { k -> remaining.any { it.key == k } }
                    ?: remaining.first().key
                keepers[group.id] = keeper
            }
            val liveKeys = groups.flatMapTo(HashSet()) { g -> g.files.map { it.key } }
            val keeperKeys = keepers.values.toHashSet()
            val preview = s.preview?.let { p ->
                val group = groups.firstOrNull { it.id == p.groupId } ?: return@let null
                if (group.files.any { it.key == p.fileKey }) p else p.copy(fileKey = keepers.getValue(group.id))
            }
            s.copy(
                groups = groups,
                keepers = keepers,
                selected = s.selected.filterTo(HashSet()) { it in liveKeys && it !in keeperKeys },
                collapsed = s.collapsed.filterTo(HashSet()) { id -> groups.any { it.id == id } },
                preview = preview,
            )
        }
    }

    // ---------------------------------------------------------------- messages

    fun showOpenFailed() {
        _state.update { it.copy(message = UiMessage.OpenFailed) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private companion object {
        /** Keeps each MediaStore delete request well under the binder transaction limit. */
        const val DELETE_BATCH = 500
    }
}
