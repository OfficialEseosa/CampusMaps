package com.campusmaps.data.shortcuts

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

// The review queue on the server (section 16.5). The phone only talks to it through this interface,
// so the fake below can be swapped for a real HTTP client without touching any screen.
interface ShortcutBackend {
    // Sends one submission. Throws IOException when the network is down.
    suspend fun submit(submission: ShortcutSubmission)

    // Latest status for everything this submitter has sent.
    suspend fun statuses(submitterId: String): List<StatusUpdate>

    // Every approved shortcut for a building, from every student.
    suspend fun approved(buildingId: String): List<ShortcutSubmission>
}

data class StatusUpdate(val id: String, val status: ShortcutStatus, val reviewerNote: String?)

// Stand-in for the real backend, used until the admin page and API exist.
// Its review queue is saved to a JSON file in app storage (attachStore), so approvals survive process death
// and reinstall-with-data (docs/22 #9). The old seeded "Sparks side cut through" pointed at node ids that do not
// exist in CS.json and was dropped (docs/22 #10).
// The debug overlay can approve or reject your pending submissions to test the whole loop.
class FakeShortcutBackend(private val isOnline: () -> Boolean) : ShortcutBackend {

    private val store = ConcurrentHashMap<String, ShortcutSubmission>()
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var file: File? = null

    // Loads the saved queue and saves every later change there. Call once, off the main thread.
    fun attachStore(storeFile: File) {
        synchronized(this) {
            file = storeFile
            if (storeFile.exists()) {
                runCatching { json.decodeFromString<List<ShortcutSubmission>>(storeFile.readText()) }
                    .onSuccess { saved -> saved.forEach { store.putIfAbsent(it.id, it) } }
                    .onFailure { log("Could not read the fake review queue: ${it.message}") }
            }
            log("Fake review queue: ${store.size} shortcut(s), ${store.values.count { it.status == ShortcutStatus.APPROVED }} approved")
        }
    }

    private fun persist() {
        val target = file ?: return
        synchronized(this) {
            runCatching {
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.writeText(json.encodeToString(store.values.sortedBy { it.createdAt }))
                if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
            }.onFailure { log("Could not save the fake review queue: ${it.message}") }
        }
    }

    override suspend fun submit(submission: ShortcutSubmission) {
        delay(400) // Feels like a network call
        if (!isOnline()) throw IOException("Offline")
        store[submission.id] = submission.copy(status = ShortcutStatus.PENDING, uploaded = true)
        persist()
    }

    override suspend fun statuses(submitterId: String): List<StatusUpdate> {
        if (!isOnline()) throw IOException("Offline")
        return store.values.filter { it.submitterId == submitterId }.map { StatusUpdate(it.id, it.status, it.reviewerNote) }
    }

    override suspend fun approved(buildingId: String): List<ShortcutSubmission> {
        if (!isOnline()) throw IOException("Offline")
        return store.values.filter { it.buildingId == buildingId && it.status == ShortcutStatus.APPROVED }
    }

    // Debug only: pretend a developer reviewed everything this submitter has pending.
    fun reviewAll(submitterId: String, approve: Boolean) {
        for ((id, s) in store) {
            if (s.submitterId == submitterId && s.status == ShortcutStatus.PENDING) {
                store[id] = if (approve) {
                    s.copy(status = ShortcutStatus.APPROVED)
                } else {
                    s.copy(status = ShortcutStatus.REJECTED, reviewerNote = "Photos too dark to check the route.")
                }
            }
        }
        persist()
    }

    // runCatching: android.util.Log is not available in JVM unit tests.
    private fun log(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private companion object {
        const val TAG = "FakeShortcutBackend"
    }
}
