package com.campusmaps.data.shortcuts

import android.content.Context
import android.util.Log
import com.campusmaps.data.SubmitterIdProvider
import com.campusmaps.data.model.GraphEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

// Keeps "Your submissions" on the phone, uploads them when the network is there,
// and pulls approved shortcuts so the router can use them.
//
// Works offline: a new submission is saved locally first (uploaded = false, row says
// "Waiting to upload") and sent as soon as the network comes back.
class ShortcutRepository(
    private val context: Context,
    private val backend: ShortcutBackend,
    private val submitterIdProvider: SubmitterIdProvider,
    private val online: StateFlow<Boolean>,
    private val notifier: ShortcutNotifier,
    private val scope: CoroutineScope,
    // Building codes of the loaded files (KL, CS, CSE). Approved shortcuts are fetched per code, the same id
    // S4 stores on a submission (Building.id = core code).
    private val buildingIds: Collection<String> = listOf("KL", "CS", "CSE"),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "shortcuts.json")
    private val lock = Mutex()

    private val _submissions = MutableStateFlow<List<ShortcutSubmission>>(emptyList())
    val submissions: StateFlow<List<ShortcutSubmission>> = _submissions.asStateFlow()

    // buildingId -> approved STUDENT edges for the router.
    private val _approvedEdges = MutableStateFlow<Map<String, List<GraphEdge>>>(emptyMap())
    val approvedEdges: StateFlow<Map<String, List<GraphEdge>>> = _approvedEdges.asStateFlow()

    init {
        scope.launch {
            // The fake review queue keeps its approvals in app storage too (docs/22 #9).
            (backend as? FakeShortcutBackend)?.let { fake ->
                withContext(Dispatchers.IO) { fake.attachStore(File(context.filesDir, "fake-review-queue.json")) }
            }
            _submissions.value = load()
            // Every time we come back online, try to catch up.
            online.filter { it }.collect { sync() } // StateFlow already skips repeats
        }
    }

    // Saves a new submission locally and tries to send it straight away.
    suspend fun submit(submission: ShortcutSubmission) {
        lock.withLock {
            _submissions.value = listOf(submission) + _submissions.value
            save()
        }
        sync()
    }

    // Upload anything queued, refresh statuses, and refresh approved shortcuts.
    suspend fun sync() {
        if (!online.value) return
        val me = submitterIdProvider.currentId()
        try {
            // 1. Upload the queue.
            for (pending in _submissions.value.filter { !it.uploaded }) {
                backend.submit(pending)
                update(pending.id) { it.copy(uploaded = true) }
            }
            // 2. Status changes from the review queue.
            for (update in backend.statuses(me)) {
                val before = _submissions.value.firstOrNull { it.id == update.id } ?: continue
                if (before.status != update.status) {
                    update(update.id) { it.copy(status = update.status, reviewerNote = update.reviewerNote) }
                    if (update.status == ShortcutStatus.APPROVED) notifier.notifyLive(before.name)
                }
            }
            // 3. Approved shortcuts from everyone, for the route graph.
            _approvedEdges.value = buildingIds.associateWith { id -> backend.approved(id).map(ShortcutRules::toEdge) }
        } catch (e: IOException) {
            Log.i(TAG, "Sync skipped, offline: ${e.message}")
        }
    }

    private suspend fun update(id: String, change: (ShortcutSubmission) -> ShortcutSubmission) {
        lock.withLock {
            _submissions.value = _submissions.value.map { if (it.id == id) change(it) else it }
            save()
        }
    }

    private suspend fun load(): List<ShortcutSubmission> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching { json.decodeFromString<List<ShortcutSubmission>>(file.readText()) }.getOrElse {
            Log.w(TAG, "Could not read saved submissions", it)
            emptyList()
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(_submissions.value))
    }

    private companion object {
        const val TAG = "ShortcutRepository"
    }
}
